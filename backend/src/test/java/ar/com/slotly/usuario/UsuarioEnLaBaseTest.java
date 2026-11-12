package ar.com.slotly.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;

// Las reglas de la tabla usuario (V8) viven en la base. Se prueban con SQL directo contra Postgres real.
@IntegrationTest
class UsuarioEnLaBaseTest {

    // Tiene la forma de un hash argon2id; para estas reglas no hace falta que sea de una contraseña real.
    static final String HASH = "$argon2id$v=19$m=16384,t=2,p=1$c2FsdA$aGFzaA";

    @Autowired
    JdbcTemplate jdbc;

    UUID localId;
    UUID anaId;

    @BeforeEach
    void prepararBase() {
        BaseDeTest.vaciar(jdbc);
        localId = crearLocal("test");
        anaId = jdbc.queryForObject("INSERT INTO profesional (local_id, nombre) VALUES (?, 'Ana') RETURNING id",
                UUID.class, localId);
    }

    @Test
    @DisplayName("un admin puede no tener agenda; un profesional siempre tiene la suya")
    void profesionalConAgenda() {
        insertarUsuario(localId, "duenio@test.com", HASH, "admin", null);

        assertThatThrownBy(() -> insertarUsuario(localId, "pro@test.com", HASH, "profesional", null))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("usuario_profesional_con_agenda");
    }

    @Test
    @DisplayName("HU-04 CA2: la base rechaza un usuario de un local con un profesional de otro local")
    void profesionalDeOtroLocal() {
        UUID otroLocalId = crearLocal("otro");

        assertThatThrownBy(() -> insertarUsuario(otroLocalId, "ana@test.com", HASH, "profesional", anaId))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("usuario_local_id_profesional_id_fkey");
    }

    @Test
    @DisplayName("HU-16 CA4: el mismo mail no se repite en un local, pero puede tener cuenta en otro")
    void mailUnicoPorLocal() {
        UUID otroLocalId = crearLocal("otro");
        insertarUsuario(localId, "ana@test.com", HASH, "admin", null);

        insertarUsuario(otroLocalId, "ana@test.com", HASH, "admin", null);

        assertThatThrownBy(() -> insertarUsuario(localId, "ana@test.com", HASH, "admin", null))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("usuario_local_id_email_key");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("el mail se guarda en minúsculas: así Ana@Test.com y ana@test.com no pueden ser dos usuarios")
    void mailEnMinusculas() {
        assertThatThrownBy(() -> insertarUsuario(localId, "Ana@Test.com", HASH, "admin", null))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("usuario_email_check");
    }

    @Test
    @DisplayName("HU-16 CA8: la base no acepta una contraseña que no esté hasheada con argon2id")
    void soloHashArgon2id() {
        assertThatThrownBy(() -> insertarUsuario(localId, "ana@test.com", "demo1234", "admin", null))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("usuario_password_hash_check");
    }

    private void insertarUsuario(UUID local, String email, String hash, String rol, UUID profesionalId) {
        jdbc.update("INSERT INTO usuario (local_id, email, password_hash, rol, profesional_id) VALUES (?, ?, ?, ?, ?)",
                local, email, hash, rol, profesionalId);
    }

    private UUID crearLocal(String subdominio) {
        return jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES (?, ?) RETURNING id",
                UUID.class, "Local de test", subdominio);
    }
}
