package ar.com.slotly.local;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

// Busca el local activo por subdominio. Lo usa la resolución del local a partir del Host.
interface LocalRepository extends JpaRepository<Local, UUID> {
    Optional<Local> findBySubdominioAndActivoTrue(String subdominio);
}
