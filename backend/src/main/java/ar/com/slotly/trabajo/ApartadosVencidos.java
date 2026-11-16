package ar.com.slotly.trabajo;

import java.sql.Timestamp;
import java.time.Clock;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Pasa a 'vencido' los apartados cuyo tiempo ya se cumplió (doc 03, "Apartados vencidos"). No es lo que libera el
// horario: la disponibilidad ya trata como libre a un apartado vencido, y quien reserva lo vence antes de insertar.
// Esto deja la tabla en orden, así nadie ve como 'apartado' algo que venció hace horas.
@Component
public class ApartadosVencidos {

    private final JdbcTemplate jdbc;
    private final Clock reloj;

    ApartadosVencidos(JdbcTemplate jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // Devuelve cuántos venció.
    public int vencer() {
        return jdbc.update("UPDATE turno SET estado = 'vencido' WHERE estado = 'apartado' AND vence_en <= ?",
                Timestamp.from(reloj.instant()));
    }
}
