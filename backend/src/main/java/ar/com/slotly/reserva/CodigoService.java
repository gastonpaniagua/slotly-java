package ar.com.slotly.reserva;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.slotly.cliente.Telefonos;
import ar.com.slotly.error.DemasiadosIntentosException;
import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.local.Local;

// Entrar con el teléfono y un código (HU-22). Al verificar el código se entrega un acceso de 30 días (decidido el
// 2026-11-12): con él el cliente ve y gestiona sus turnos del local desde cualquier navegador.
@Service
public class CodigoService {

    static final int DIGITOS = 6;
    static final Duration VIGENCIA = Duration.ofMinutes(10);
    static final int MAXIMO_INTENTOS = 5;
    static final int MAXIMO_PEDIDOS_POR_HORA = 3;
    static final Duration DURACION_ACCESO = Duration.ofDays(30);

    private static final SecureRandom AZAR = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final Clock reloj;
    private final EnvioDeCodigos envio;

    CodigoService(JdbcTemplate jdbc, Clock reloj, EnvioDeCodigos envio) {
        this.jdbc = jdbc;
        this.reloj = reloj;
        this.envio = envio;
    }

    // El acceso que se le da al cliente: el token (va al navegador; en la base, solo su hash) y cuándo vence.
    public record Acceso(String token, Instant venceEn) {
    }

    // HU-22 CA1 y CA2: genera un código y lo manda solo si el teléfono es de un cliente del local. Para quien llama,
    // las dos situaciones son idénticas: misma respuesta, misma fila guardada, mismo límite por hora (CA6).
    @Transactional
    public void pedir(Local local, String telefonoEscrito) {
        String telefono = normalizar(telefonoEscrito);
        Instant ahora = reloj.instant();
        // Un pedido a la vez por teléfono: dos pedidos simultáneos no pasan los dos el límite.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", local.getId() + "|" + telefono);
        Integer pedidosDeLaUltimaHora = jdbc.queryForObject("""
                SELECT count(*) FROM codigo_verificacion WHERE local_id = ? AND telefono = ? AND creado_en > ?
                """, Integer.class, local.getId(), telefono, Timestamp.from(ahora.minus(Duration.ofHours(1))));
        if (pedidosDeLaUltimaHora != null && pedidosDeLaUltimaHora >= MAXIMO_PEDIDOS_POR_HORA) {
            throw new DemasiadosIntentosException("Pediste muchos códigos. Probá de nuevo en una hora.", 3600);
        }

        String codigo = String.format("%0" + DIGITOS + "d", AZAR.nextInt((int) Math.pow(10, DIGITOS)));
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO codigo_verificacion (id, local_id, telefono, codigo_hash, creado_en, vence_en)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, local.getId(), telefono, hashDelCodigo(id, codigo), Timestamp.from(ahora),
                Timestamp.from(ahora.plus(VIGENCIA)));
        if (clienteDe(local, telefono) != null) {
            envio.enviar(telefono, codigo);
        }
    }

    // HU-22 CA3 a CA5: con el último código del teléfono, sin vencer ni usar, y con menos de 5 intentos fallidos,
    // entrega el acceso. Cualquier otro caso (incluido un teléfono sin turnos) responde lo mismo: CODIGO_INVALIDO.
    @Transactional(noRollbackFor = SlotlyException.class)
    public Acceso verificar(Local local, String telefonoEscrito, String codigo) {
        String telefono = normalizar(telefonoEscrito);
        Instant ahora = reloj.instant();
        // El último código pedido; la fila queda bloqueada para que dos intentos a la vez no sumen mal (CA4).
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT id, codigo_hash, intentos, vence_en, usado_en FROM codigo_verificacion
                WHERE local_id = ? AND telefono = ?
                ORDER BY creado_en DESC LIMIT 1
                FOR UPDATE
                """, local.getId(), telefono);
        if (filas.isEmpty()) {
            throw codigoInvalido();
        }
        Map<String, Object> fila = filas.get(0);
        UUID id = (UUID) fila.get("id");
        boolean vigente = fila.get("usado_en") == null
                && ((Timestamp) fila.get("vence_en")).toInstant().isAfter(ahora)
                && (Integer) fila.get("intentos") < MAXIMO_INTENTOS;
        UUID clienteId = clienteDe(local, telefono);
        boolean coincide = hashDelCodigo(id, codigo.trim()).equals(fila.get("codigo_hash"));

        if (!vigente || !coincide || clienteId == null) {
            if (vigente) {
                // CA4: cada fallo cuenta; con el quinto, el código ya no sirve aunque después llegue el correcto.
                jdbc.update("UPDATE codigo_verificacion SET intentos = intentos + 1 WHERE id = ?", id);
            }
            throw codigoInvalido();
        }

        // CA5: usado una vez, no vale más.
        jdbc.update("UPDATE codigo_verificacion SET usado_en = ? WHERE id = ?", Timestamp.from(ahora), id);
        String token = TokenSecreto.nuevo();
        Instant venceEn = ahora.plus(DURACION_ACCESO);
        jdbc.update("""
                INSERT INTO acceso_cliente (local_id, cliente_id, token_hash, creado_en, vence_en) VALUES (?, ?, ?, ?, ?)
                """, local.getId(), clienteId, TokenSecreto.hash(token), Timestamp.from(ahora), Timestamp.from(venceEn));
        return new Acceso(token, venceEn);
    }

    // El cliente del acceso, si el token existe, es de este local y no venció. Si no, 401: hay que volver a entrar.
    @Transactional(readOnly = true)
    public UUID clienteDelAcceso(Local local, String token) {
        List<UUID> clientes = jdbc.queryForList("""
                SELECT cliente_id FROM acceso_cliente WHERE token_hash = ? AND local_id = ? AND vence_en > ?
                """, UUID.class, TokenSecreto.hash(token), local.getId(), Timestamp.from(reloj.instant()));
        if (clientes.isEmpty()) {
            throw new SlotlyException(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                    "Tenés que volver a entrar con tu teléfono.");
        }
        return clientes.get(0);
    }

    // El cliente del local con ese teléfono, si existe (solo existe si alguna vez reservó, HU-10 CA3).
    private UUID clienteDe(Local local, String telefono) {
        List<UUID> clientes = jdbc.queryForList("SELECT id FROM cliente WHERE local_id = ? AND telefono = ?",
                UUID.class, local.getId(), telefono);
        return clientes.isEmpty() ? null : clientes.get(0);
    }

    private static String normalizar(String telefono) {
        return Telefonos.normalizar(telefono).orElseThrow(() -> new SlotlyException(HttpStatus.BAD_REQUEST,
                "VALIDACION", "Revisá el número de celular."));
    }

    // CA7: en la base, solo el hash. El id de la fila hace de sal: el mismo código en dos filas da hashes distintos.
    private static String hashDelCodigo(UUID id, String codigo) {
        return TokenSecreto.hash(id + ":" + codigo);
    }

    private static SlotlyException codigoInvalido() {
        return new SlotlyException(HttpStatus.BAD_REQUEST, "CODIGO_INVALIDO",
                "El código no es válido o venció. Pedí uno nuevo si hace falta.");
    }
}
