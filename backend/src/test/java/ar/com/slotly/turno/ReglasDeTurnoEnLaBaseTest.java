package ar.com.slotly.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ar.com.slotly.IntegrationTest;

// Las reglas de turno viven en la base (doc 03). Se prueban con SQL directo contra Postgres real.
@IntegrationTest
class ReglasDeTurnoEnLaBaseTest {

    @Autowired
    JdbcTemplate jdbc;

    UUID localId;
    UUID anaId;

    @BeforeEach
    void prepararBase() {
        jdbc.execute("TRUNCATE local, servicio, profesional, cliente, turno, turno_servicio, profesional_servicio, horario");
        localId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES (?, ?) RETURNING id", UUID.class, "Local de test", "test");
        anaId = jdbc.queryForObject("INSERT INTO profesional (local_id, nombre ) VALUES (?,? ) RETURNING id", UUID.class, localId, "Ana");
    }

    @Test
    @DisplayName("HU-13 CA1: la base rechaza un turno que se pisa con otro del mismo profesional")
    void rechazaTurnoSuperpuesto() {
        insertarBloqueo(anaId, "2026-11-03T10:00:00-03:00", "2026-11-03T10:45:00-03:00");
        
        assertThatThrownBy(() -> insertarBloqueo(anaId, "2026-11-03T10:30:00-03:00", "2026-11-03T11:00:00-03:00"))   
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("turno_sin_superposicion");
    }

    @Test
    @DisplayName("HU-13 CA2: la base acepta un turno que empieza justo cuando termina otro")
    void aceptaTurnosConsecutivos() {
        insertarBloqueo(anaId, "2026-11-03T10:00:00-03:00", "2026-11-03T10:45:00-03:00");
        insertarBloqueo(anaId, "2026-11-03T10:45:00-03:00", "2026-11-03T11:30:00-03:00");
        Integer cantidad = jdbc.queryForObject("SELECT count(*) FROM turno", Integer.class);
        assertThat(cantidad).isEqualTo(2);
    }

    // Inserta un bloqueo confirmado (sin cliente) del profesional, de inicio a fin.
    private void insertarBloqueo(UUID profesionalId, String inicio, String fin) {
        jdbc.update("""
                INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta)
                VALUES (?, ?, 'bloqueo', 'confirmado', ?::timestamptz, ?::timestamptz, ?::timestamptz)
                """, localId, profesionalId, inicio, fin, fin);
    }
}