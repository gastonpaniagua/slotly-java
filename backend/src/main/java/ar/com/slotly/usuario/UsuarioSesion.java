package ar.com.slotly.usuario;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

// Lo que la sesión recuerda del usuario que entró. Se guarda serializado en spring_session_attributes, así que
// lleva lo justo para decidir permisos sin ir a la base en cada pedido. Nunca la contraseña ni su hash.
public record UsuarioSesion(UUID id, UUID localId, String email, String rol, UUID profesionalId)
        implements Principal, Serializable {

    public static final String ADMIN = "admin";

    public static UsuarioSesion de(Usuario usuario) {
        return new UsuarioSesion(usuario.getId(), usuario.getLocalId(), usuario.getEmail(), usuario.getRol(),
                usuario.getProfesionalId());
    }

    // El nombre de la sesión (columna principal_name): el id, para poder cerrar todas las sesiones de un usuario
    // cuando se lo desactive (Etapa 7).
    @Override
    public String getName() {
        return id.toString();
    }

    public boolean esAdmin() {
        return ADMIN.equals(rol);
    }
}
