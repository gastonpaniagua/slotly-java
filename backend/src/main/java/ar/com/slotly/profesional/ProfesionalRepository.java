package ar.com.slotly.profesional;

import java.time.Instant;
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

    // Los de la agenda "Todos" del admin (HU-18): los activos y también los dados de baja que todavía tienen turnos
    // confirmados por delante, para poder moverlos o cancelarlos (decidido el 2026-11-13, HU-25 CA3).
    @Query("""
            SELECT p FROM Profesional p
            WHERE p.localId = :localId
              AND (p.activo = true
                   OR EXISTS (SELECT 1 FROM Turno t WHERE t.localId = p.localId AND t.profesionalId = p.id
                              AND t.estado = 'confirmado' AND t.tipo <> 'bloqueo' AND t.inicio > :ahora))
            ORDER BY p.orden, p.nombre
            """)
    List<Profesional> paraLaAgenda(@Param("localId") UUID localId, @Param("ahora") Instant ahora);
}
