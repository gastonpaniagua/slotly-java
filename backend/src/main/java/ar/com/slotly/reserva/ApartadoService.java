package ar.com.slotly.reserva;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonFormat;

import ar.com.slotly.disponibilidad.DisponibilidadService;
import ar.com.slotly.disponibilidad.DisponibilidadService.HorarioReservable;
import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.local.Local;
import ar.com.slotly.servicio.Servicio;

// Aparta un horario mientras el cliente completa sus datos (HU-11). El apartado es un turno en estado 'apartado'
// que vence a los apartado_min minutos: así pasa por el mismo constraint que los turnos y nadie más puede tomar
// esa hora (doc 03). Que dos no aparten lo mismo lo garantiza la base, no este código.
@Service
public class ApartadoService {

    private final DisponibilidadService disponibilidad;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    ApartadoService(DisponibilidadService disponibilidad, JdbcTemplate jdbc, Clock reloj) {
        this.disponibilidad = disponibilidad;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // Lo que pidió el cliente. anterior: el apartado que tenía antes (si volvió y eligió otra hora), para liberarlo.
    public record Pedido(Set<UUID> servicios, String profesional, LocalDate fecha, LocalTime hora, Anterior anterior) {
    }

    public record Anterior(UUID id, String token) {
    }

    // El apartado creado. El token se devuelve esta única vez: en la base solo queda su hash.
    public record Apartado(UUID id, String token, Instant venceEn, int segundosParaConfirmar,
            UUID profesionalId, String profesionalNombre, LocalDate fecha,
            @JsonFormat(pattern = "HH:mm") LocalTime hora, int duracionMin, int precioTotal) {
    }

    @Transactional
    public Apartado apartar(Local local, Pedido pedido) {
        Instant ahora = reloj.instant();
        // HU-11 CA3: si volvió y eligió otra hora, el apartado anterior se libera primero
        // (así también puede volver a elegir la misma hora sin chocar consigo mismo).
        if (pedido.anterior() != null) {
            liberar(local, pedido.anterior());
        }

        HorarioReservable horario = disponibilidad
                .reservable(local, pedido.servicios(), pedido.profesional(), pedido.fecha(), pedido.hora())
                .orElseThrow(ApartadoService::horarioOcupado);

        // Fila por profesional: con muchos pedidos a la vez, dos inserts que se pisan se esperan uno al otro en el
        // constraint de exclusión y Postgres corta a uno por deadlock. Con este candado (se suelta solo al terminar
        // la transacción) entran de a uno y el segundo ve lo que guardó el primero. El constraint sigue siendo la
        // garantía final.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))", horario.profesional().getId());
        if (!seguiaLibre(local, pedido, horario)) {
            throw horarioOcupado();
        }

        // Los apartados vencidos que siguen como 'apartado' todavía bloquean en el constraint (no puede depender de
        // now()): se pasan a 'vencido' antes de insertar (doc 03, "Apartados vencidos").
        jdbc.update("""
                UPDATE turno SET estado = 'vencido'
                WHERE local_id = ? AND profesional_id = ? AND estado = 'apartado' AND vence_en <= ?
                  AND inicio < ? AND ocupado_hasta > ?
                """, local.getId(), horario.profesional().getId(), Timestamp.from(ahora),
                Timestamp.from(horario.ocupadoHasta()), Timestamp.from(horario.inicio()));

        String token = TokenSecreto.nuevo();
        Instant venceEn = ahora.plus(Duration.ofMinutes(local.getApartadoMin()));
        UUID id;
        try {
            id = jdbc.queryForObject("""
                    INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta, vence_en,
                                       token_hash, token_vence_en)
                    VALUES (?, ?, 'reserva', 'apartado', ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, UUID.class, local.getId(), horario.profesional().getId(),
                    Timestamp.from(horario.inicio()), Timestamp.from(horario.fin()),
                    Timestamp.from(horario.ocupadoHasta()), Timestamp.from(venceEn),
                    TokenSecreto.hash(token), Timestamp.from(venceEn));
        } catch (DataIntegrityViolationException e) {
            // Otro lo tomó entre que calculamos y guardamos: el constraint de exclusión lo frenó (HU-13 CA4).
            if (String.valueOf(e.getMessage()).contains("turno_sin_superposicion")) {
                throw horarioOcupado();
            }
            throw e;
        } catch (PessimisticLockingFailureException e) {
            // Por si igual hay un deadlock (por ejemplo, con un turno cargado desde el panel): el otro entró.
            throw horarioOcupado();
        }

        // HU-10 CA8: la duración y el precio de cada servicio se copian; cambiarlos después no toca este turno.
        for (Servicio servicio : horario.servicios()) {
            jdbc.update("""
                    INSERT INTO turno_servicio (local_id, turno_id, servicio_id, duracion_min, precio)
                    VALUES (?, ?, ?, ?, ?)
                    """, local.getId(), id, servicio.getId(), servicio.getDuracionMin(), servicio.getPrecio());
        }

        List<Servicio> servicios = horario.servicios();
        return new Apartado(id, token, venceEn, local.getApartadoMin() * 60,
                horario.profesional().getId(), horario.profesional().getNombre(), pedido.fecha(), pedido.hora(),
                servicios.stream().mapToInt(Servicio::getDuracionMin).sum(),
                servicios.stream().mapToInt(Servicio::getPrecio).sum());
    }

    // Ya con el candado: ¿el profesional elegido sigue libre? Si otro pedido entró mientras esperábamos, no.
    // (Con "cualquiera" no se elige a otro: el cliente ve la lista actualizada y vuelve a elegir.)
    private boolean seguiaLibre(Local local, Pedido pedido, HorarioReservable horario) {
        return disponibilidad
                .reservable(local, pedido.servicios(), horario.profesional().getId().toString(), pedido.fecha(), pedido.hora())
                .isPresent();
    }

    // Libera un apartado propio (lo pasa a 'vencido'). Si el token no corresponde o ya no está apartado, no hace nada:
    // nadie puede liberar el apartado de otro.
    private void liberar(Local local, Anterior anterior) {
        jdbc.update("""
                UPDATE turno SET estado = 'vencido'
                WHERE id = ? AND local_id = ? AND estado = 'apartado' AND token_hash = ?
                """, anterior.id(), local.getId(), TokenSecreto.hash(anterior.token()));
    }

    // HU-11 CA5 y HU-12 CA3: ocupado, apartado por otro o ya no disponible responden lo mismo.
    private static SlotlyException horarioOcupado() {
        return new SlotlyException(HttpStatus.CONFLICT, "HORARIO_OCUPADO", "Ese horario se acaba de ocupar, elegí otro.");
    }
}
