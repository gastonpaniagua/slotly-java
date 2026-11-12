package ar.com.slotly.usuario;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    // El login busca el mail dentro del local del subdominio: un usuario de otro local no aparece (HU-16 CA4).
    Optional<Usuario> findByLocalIdAndEmailAndActivoTrue(UUID localId, String email);
}
