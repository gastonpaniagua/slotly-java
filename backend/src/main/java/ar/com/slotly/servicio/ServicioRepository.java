package ar.com.slotly.servicio;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

// Servicios de un local. Siempre se filtra por localId: un local nunca ve los servicios de otro (HU-04).
public interface ServicioRepository extends JpaRepository<Servicio, UUID> {

    // Los que se muestran en la home: activos, en el orden que eligió el local (y por nombre si empatan).
    List<Servicio> findByLocalIdAndActivoTrueOrderByOrdenAscNombreAsc(UUID localId);

    // Cuántos de esos ids son servicios activos de este local: si no son todos, alguno es ajeno, oculto o no existe.
    long countByLocalIdAndActivoTrueAndIdIn(UUID localId, Collection<UUID> ids);
}
