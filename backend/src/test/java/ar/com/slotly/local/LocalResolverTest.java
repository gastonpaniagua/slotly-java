package ar.com.slotly.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ar.com.slotly.IntegrationTest;
import ar.com.slotly.error.SlotlyException;

// Prueba que el LocalResolver encuentra el local por el Host. Se prueba contra Postgres real.
@IntegrationTest
class LocalResolverTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LocalResolver resolver;

    @BeforeEach
    void limpiarBase() {
        jdbc.execute("TRUNCATE local, servicio, profesional, cliente, turno, turno_servicio, profesional_servicio, horario");
    }

    @Test
    @DisplayName("HU-03 CA1: con el subdominio de un local activo devuelve ese local y no otro")
    void devuelveElLocalDeEseSubdominio() {
        UUID demoId = crearLocal("demo");
        crearLocal("otra");

        Local local = resolver.resolver("demo.localhost:5173");

        assertThat(local.getId()).isEqualTo(demoId);
    }

    @Test
    @DisplayName("HU-03 CA2: un subdominio que no existe responde LOCAL_NO_ENCONTRADO")
    void subdominioInexistenteDa404() {
        crearLocal("demo");

        assertThatThrownBy(() -> resolver.resolver("noexiste.localhost"))
                .isInstanceOf(SlotlyException.class)
                .hasFieldOrPropertyWithValue("codigo", "LOCAL_NO_ENCONTRADO");
    }

    @Test
    @DisplayName("HU-03 CA2: un local inactivo responde igual que uno que no existe")
    void localInactivoDa404() {
        UUID demoId = crearLocal("demo");
        jdbc.update("UPDATE local SET activo = false WHERE id = ?", demoId);

        assertThatThrownBy(() -> resolver.resolver("demo.localhost"))
                .isInstanceOf(SlotlyException.class)
                .hasFieldOrPropertyWithValue("codigo", "LOCAL_NO_ENCONTRADO");
    }

    // Crea un local activo con ese subdominio y devuelve su id.
    private UUID crearLocal(String subdominio) {
        return jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES (?, ?) RETURNING id",
                UUID.class, "Local de test", subdominio);
    }
}
