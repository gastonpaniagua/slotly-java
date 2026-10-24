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
        localId = crearLocal("test");
        anaId = crearProfesional(localId, "Ana");
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
    
    @Test
    @DisplayName("HU-13 CA3: un turno cancelado libera el horario")
    void turnoCanceladoLiberaElHorario() {
        insertarBloqueo(anaId, "2026-11-03T10:00:00-03:00", "2026-11-03T10:45:00-03:00");
        jdbc.update("UPDATE turno SET estado = 'cancelado'");

        insertarBloqueo(anaId, "2026-11-03T10:00:00-03:00", "2026-11-03T10:45:00-03:00");

        Integer cantidad = jdbc.queryForObject("SELECT count(*) FROM turno", Integer.class);
        assertThat(cantidad).isEqualTo(2);
    }
    
    @Test
    @DisplayName("HU-13 CA5: otro profesional puede tener un turno en el mismo horario")
    void otroProfesionalEnElMismoHorario() {
        UUID brunoId = crearProfesional(localId, "Bruno");
        insertarBloqueo(anaId, "2026-11-03T10:00:00-03:00", "2026-11-03T10:45:00-03:00");
        
        insertarBloqueo(brunoId, "2026-11-03T10:00:00-03:00", "2026-11-03T10:45:00-03:00");
        
        Integer cantidad = jdbc.queryForObject("SELECT count(*) FROM turno", Integer.class);
        assertThat(cantidad).isEqualTo(2);
    }
    @Test
    @DisplayName("HU-04 CA2: la base rechaza un turno de un local con un profesional de otro local")
    void rechazaTurnoConProfesionalDeOtroLocal() {
        UUID otroLocalId = crearLocal("otro");
        UUID carlaId = crearProfesional(otroLocalId, "Carla");

        assertThatThrownBy(() -> insertarBloqueo(carlaId, "2026-11-03T10:00:00-03:00", "2026-11-03T10:45:00-03:00"))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("turno_local_id_profesional_id_fkey");
    }

    // Inserta un bloqueo confirmado (sin cliente) del profesional, de inicio a fin.
    private void insertarBloqueo(UUID profesionalId, String inicio, String fin) {
        jdbc.update("""
                INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta)
                VALUES (?, ?, 'bloqueo', 'confirmado', ?::timestamptz, ?::timestamptz, ?::timestamptz)
                """, localId, profesionalId, inicio, fin, fin);
    }
        // Crea un local con ese subdominio y devuelve su id.
    private UUID crearLocal(String subdominio) {
        
        return jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES (?, ?) RETURNING id", UUID.class, "Local de test", subdominio);

    }

    // Crea un profesional en ese local y devuelve su id.
    private UUID crearProfesional(UUID local, String nombre) {
        return jdbc.queryForObject("INSERT INTO profesional (local_id, nombre) VALUES (?, ?) RETURNING id", UUID.class, local, nombre);
    }
    
   
}