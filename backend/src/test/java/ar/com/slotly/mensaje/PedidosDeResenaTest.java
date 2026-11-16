package ar.com.slotly.mensaje;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.MensajesEnviadosDeTest;
import ar.com.slotly.MensajesEnviadosDeTest.Enviado;
import ar.com.slotly.RelojDeTest;

// HU-38: el pedido de reseña, contra Postgres real.
// Reloj: martes 17/11/2026 a las 13:00 en Buenos Aires (16:00 UTC). Los turnos de Lucía duran 30 minutos; uno que
// terminó a las 11:00 (14:00 UTC) cumple justo las 2 horas.
@IntegrationTest
class PedidosDeResenaTest {

    private static final Instant MARTES_13 = Instant.parse("2026-11-17T16:00:00Z");
    private static final String RESENA = "https://g.page/r/barberia-demo/review";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RelojDeTest reloj;

    @Autowired
    MensajesEnviadosDeTest enviados;

    @Autowired
    PedidosDeResena pedidos;

    @Autowired
    TrabajoDeMensajes trabajo;

    UUID demoId;
    UUID anaId;
    UUID luciaId;

    @BeforeEach
    void preparar() {
        reloj.fijar(MARTES_13);
        enviados.reiniciar();
        BaseDeTest.vaciar(jdbc);
        demoId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio, link_resena) VALUES ('Barbería Demo', 'demo', ?) RETURNING id",
                UUID.class, RESENA);
        anaId = jdbc.queryForObject("INSERT INTO profesional (local_id, nombre) VALUES (?, 'Ana') RETURNING id", UUID.class, demoId);
        luciaId = cliente("Lucía", "+5491123456789");
    }

    @Test
    @DisplayName("HU-38 CA1: 2 horas después de un turno marcado 'vino' sale el pedido con el link de reseña del local")
    void despuesDeVino() {
        turno(luciaId, "2026-11-17T13:30:00Z", "completado");   // terminó a las 11:00

        assertThat(pedidos.encolar()).isEqualTo(1);
        trabajo.enviarPendientes();

        assertThat(enviados.enviados()).containsExactly(new Enviado("+5491123456789",
                "Hola, Lucía. ¡Gracias por venir a Barbería Demo! Si te gustó, nos ayuda mucho que nos dejes una reseña: " + RESENA));
        assertThat(jdbc.queryForObject("SELECT ultima_resena_pedida_en FROM cliente WHERE id = ?", Timestamp.class, luciaId)
                .toInstant()).isEqualTo(MARTES_13);
    }

    @Test
    @DisplayName("HU-38 CA1: también sale para un turno que terminó sin marcar; un minuto antes de las 2 horas, todavía no")
    void sinMarcarYJustoALas2Horas() {
        UUID turno = turno(luciaId, "2026-11-17T13:31:00Z", "confirmado");   // termina a las 11:01

        assertThat(pedidos.encolar()).isZero();
        reloj.fijar(MARTES_13.plusSeconds(60));

        assertThat(pedidos.encolar()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensaje WHERE turno_id = ? AND tipo = 'resena'", Integer.class, turno))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("HU-38 CA2: si al cliente se le pidió hace menos de 90 días no sale; con 90 días, sí")
    void cada90Dias() {
        turno(luciaId, "2026-11-17T13:30:00Z", "completado");
        jdbc.update("UPDATE cliente SET ultima_resena_pedida_en = ? WHERE id = ?",
                Timestamp.from(MARTES_13.minusSeconds(90L * 24 * 3600 - 60)), luciaId);

        assertThat(pedidos.encolar()).isZero();

        jdbc.update("UPDATE cliente SET ultima_resena_pedida_en = ? WHERE id = ?",
                Timestamp.from(MARTES_13.minusSeconds(90L * 24 * 3600)), luciaId);
        assertThat(pedidos.encolar()).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-38 CA2: con dos turnos terminados el mismo día, el cliente recibe un solo pedido")
    void dosTurnosUnPedido() {
        turno(luciaId, "2026-11-17T12:00:00Z", "completado");
        turno(luciaId, "2026-11-17T13:00:00Z", "completado");

        assertThat(pedidos.encolar()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensaje", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-38 CA3: un turno cancelado o 'no vino' no recibe pedido")
    void canceladoONoVino() {
        turno(luciaId, "2026-11-17T12:00:00Z", "cancelado");
        turno(cliente("Pedro", "+5491155554444"), "2026-11-17T13:00:00Z", "ausente");

        assertThat(pedidos.encolar()).isZero();
    }

    @Test
    @DisplayName("HU-38 CA3: si después de encolarlo se marca 'no vino', el pedido se descarta sin salir")
    void marcadoNoVinoDespues() {
        UUID turno = turno(luciaId, "2026-11-17T13:30:00Z", "confirmado");
        pedidos.encolar();
        jdbc.update("UPDATE turno SET estado = 'ausente' WHERE id = ?", turno);

        trabajo.enviarPendientes();

        assertThat(enviados.enviados()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT estado FROM mensaje WHERE turno_id = ?", String.class, turno)).isEqualTo("descartado");
    }

    @Test
    @DisplayName("HU-38 CA4: un local sin link de reseña no manda pedidos")
    void sinLinkDeResena() {
        jdbc.update("UPDATE local SET link_resena = NULL WHERE id = ?", demoId);
        turno(luciaId, "2026-11-17T13:30:00Z", "completado");

        assertThat(pedidos.encolar()).isZero();
    }

    @Test
    @DisplayName("HU-38 CA5: si los trabajos corren dos veces, sale un solo pedido")
    void corriendoDosVeces() {
        turno(luciaId, "2026-11-17T13:30:00Z", "completado");

        pedidos.encolar();
        pedidos.encolar();
        trabajo.enviarPendientes();
        trabajo.enviarPendientes();

        assertThat(enviados.enviados()).hasSize(1);
    }

    @Test
    @DisplayName("HU-38: no sale para un cliente sin teléfono ni para turnos de hace más de 3 días")
    void sinTelefonoOViejo() {
        turno(cliente("Juan", null), "2026-11-17T13:30:00Z", "completado");
        turno(luciaId, "2026-11-14T13:00:00Z", "completado");   // sábado: hace más de 3 días

        assertThat(pedidos.encolar()).isZero();
    }

    private UUID cliente(String nombre, String telefono) {
        return jdbc.queryForObject("INSERT INTO cliente (local_id, nombre, telefono) VALUES (?, ?, ?) RETURNING id",
                UUID.class, demoId, nombre, telefono);
    }

    // Un turno de 30 minutos con Ana que empieza a esa hora (UTC).
    private UUID turno(UUID cliente, String inicio, String estado) {
        Instant desde = Instant.parse(inicio);
        return jdbc.queryForObject("""
                INSERT INTO turno (local_id, profesional_id, cliente_id, tipo, estado, inicio, fin, ocupado_hasta)
                VALUES (?, ?, ?, 'manual', ?, ?, ?, ?) RETURNING id
                """, UUID.class, demoId, anaId, cliente, estado, Timestamp.from(desde),
                Timestamp.from(desde.plusSeconds(1800)), Timestamp.from(desde.plusSeconds(1800)));
    }
}
