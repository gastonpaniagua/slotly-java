package ar.com.slotly.servicio;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

// Servicios de un local. Siempre se filtra por localId: un local nunca ve los servicios de otro (HU-04).
public interface ServicioRepository extends JpaRepository<Servicio, UUID> {

    // Los que se muestran en la home: activos, en el orden que eligió el local (y por nombre si empatan).
    List<Servicio> findByLocalIdAndActivoTrueOrderByOrdenAscNombreAsc(UUID localId);

    // Los servicios activos de este local que estén entre esos ids: si faltan, alguno es ajeno, oculto o no existe.
    List<Servicio> findByLocalIdAndActivoTrueAndIdIn(UUID localId, Collection<UUID> ids);
}
