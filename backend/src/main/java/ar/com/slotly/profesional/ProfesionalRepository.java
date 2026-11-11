package ar.com.slotly.profesional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Profesionales de un local. Siempre se filtra por localId (HU-04).
public interface ProfesionalRepository extends JpaRepository<Profesional, UUID> {

    // Los profesionales activos del local que hacen TODOS los servicios pedidos (HU-06 CA1).
    // Cuenta cuántos de esos servicios tiene cada uno en profesional_servicio: tiene que tenerlos todos.
    // Va en SQL nativo porque profesional_servicio no tiene entidad (es solo la tabla que une los dos).
    @Query(nativeQuery = true, value = """
            SELECT p.* FROM profesional p
            WHERE p.local_id = :localId
              AND p.activo
              AND (SELECT count(*) FROM profesional_servicio ps
                   WHERE ps.profesional_id = p.id AND ps.servicio_id IN (:servicios)) = :cantidad
            ORDER BY p.orden, p.nombre
            """)
    List<Profesional> buscarActivosQueHacenTodos(@Param("localId") UUID localId,
            @Param("servicios") Collection<UUID> servicios, @Param("cantidad") int cantidad);
}
