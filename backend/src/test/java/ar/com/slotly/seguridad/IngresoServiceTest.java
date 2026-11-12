package ar.com.slotly.seguridad;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.local.Local;
import ar.com.slotly.usuario.Usuario;
import ar.com.slotly.usuario.UsuarioRepository;

// HU-16 CA2 "tarda lo mismo": medir milisegundos sería un test frágil. Se prueba la causa: con un mail que no
// existe se hace la misma comparación argon2id (la parte lenta) que con uno que existe.
class IngresoServiceTest {

    PasswordEncoder encoder = spy(new SecurityConfig().passwordEncoder());
    UsuarioRepository usuarios = mock(UsuarioRepository.class);
    IngresoService ingreso;
    Local local = mock(Local.class);

    @BeforeEach
    void preparar() {
        when(local.getId()).thenReturn(UUID.randomUUID());
        ingreso = new IngresoService(usuarios, encoder, new IntentosDeIngreso(Clock.systemUTC()));
        clearInvocations(encoder);   // el constructor ya usó el encoder para el hash de relleno
    }

    @Test
    @DisplayName("HU-16 CA2: con un mail que no existe igual se compara la contraseña con argon2id, una vez")
    void mailInexistenteTambienCompara() {
        when(usuarios.findByLocalIdAndEmailAndActivoTrue(any(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingreso.ingresar(local, "nadie@demo.com", "clave")).isInstanceOf(SlotlyException.class);

        verify(encoder, times(1)).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("HU-16 CA2: con un mail que existe y la contraseña mal, también una comparación")
    void contraseniaIncorrectaCompara() {
        String hash = encoder.encode("la-buena");
        Usuario ana = mock(Usuario.class);
        when(ana.getPasswordHash()).thenReturn(hash);
        clearInvocations(encoder);
        when(usuarios.findByLocalIdAndEmailAndActivoTrue(any(), anyString())).thenReturn(Optional.of(ana));

        assertThatThrownBy(() -> ingreso.ingresar(local, "ana@demo.com", "otra")).isInstanceOf(SlotlyException.class);

        verify(encoder, times(1)).matches(anyString(), anyString());
    }
}
