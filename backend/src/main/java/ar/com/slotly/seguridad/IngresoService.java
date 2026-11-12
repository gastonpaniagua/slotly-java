package ar.com.slotly.seguridad;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import ar.com.slotly.error.DemasiadosIntentosException;
import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.local.Local;
import ar.com.slotly.usuario.Usuario;
import ar.com.slotly.usuario.UsuarioRepository;
import ar.com.slotly.usuario.UsuarioSesion;

// Verifica mail y contraseña de un usuario del panel (HU-16). No sabe nada de HTTP ni de sesiones: eso es del
// controlador. Acá están las reglas: buscar solo en el local del subdominio, no delatar si el mail existe y
// frenar a quien prueba contraseñas.
@Service
public class IngresoService {

    static final String MENSAJE_INVALIDO = "Mail o contraseña incorrectos.";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final IntentosDeIngreso intentos;
    // Hash de una contraseña que nadie tiene. Si el mail no existe, se compara contra este: así la respuesta tarda
    // lo mismo que con un mail que existe y la contraseña mal (HU-16 CA2), y nadie averigua qué mails hay.
    private final String hashDeRelleno;

    IngresoService(UsuarioRepository usuarios, PasswordEncoder encoder, IntentosDeIngreso intentos) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.intentos = intentos;
        this.hashDeRelleno = encoder.encode(UUID.randomUUID().toString());
    }

    public UsuarioSesion ingresar(Local local, String email, String password) {
        String mail = email.trim().toLowerCase();
        String clave = local.getId() + "|" + mail;

        // HU-16 CA3: bloqueado, ni se mira la contraseña (aunque esta vez sea la correcta).
        Optional<Duration> bloqueo = intentos.bloqueo(clave);
        if (bloqueo.isPresent()) {
            throw new DemasiadosIntentosException("Demasiados intentos. Probá de nuevo en 15 minutos.",
                    bloqueo.get().toSeconds());
        }

        // HU-16 CA4: solo los usuarios del local del subdominio; uno de otro local es "mail incorrecto".
        Optional<Usuario> usuario = usuarios.findByLocalIdAndEmailAndActivoTrue(local.getId(), mail);
        // Siempre una comparación argon2id, exista o no el mail (CA2).
        boolean coincide = encoder.matches(password, usuario.map(Usuario::getPasswordHash).orElse(hashDeRelleno));

        if (usuario.isEmpty() || !coincide) {
            intentos.registrarFallo(clave);
            throw new SlotlyException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", MENSAJE_INVALIDO);
        }
        intentos.registrarExito(clave);
        return UsuarioSesion.de(usuario.get());
    }
}
