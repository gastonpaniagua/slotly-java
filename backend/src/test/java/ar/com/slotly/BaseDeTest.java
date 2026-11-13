package ar.com.slotly;

import org.springframework.jdbc.core.JdbcTemplate;

// Deja la base de tests vacía antes de cada test: cada uno arma sus propios datos (sin datos compartidos).
public final class BaseDeTest {

    private BaseDeTest() {
    }

    // Todas las tablas en un solo TRUNCATE: Postgres no deja vaciar una tabla si otra que la referencia queda afuera.
    // Al agregar una tabla en una migración, se suma acá.
    public static void vaciar(JdbcTemplate jdbc) {
        jdbc.execute("""
                TRUNCATE local, servicio, profesional, cliente, turno, turno_servicio, profesional_servicio, horario,
                         usuario, spring_session, spring_session_attributes, codigo_verificacion, acceso_cliente, imagen
                """);
    }
}
