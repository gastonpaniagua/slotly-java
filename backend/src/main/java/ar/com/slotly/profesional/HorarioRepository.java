package ar.com.slotly.profesional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Franjas horarias de los profesionales. Siempre se filtra por localId (HU-04).
public interface HorarioRepository extends JpaRepository<Horario, UUID> {

    // Las franjas de los profesionales activos del local: con ellas se arma el horario de atención (doc 00).
    // Va con @Query porque Horario guarda el profesional como id simple y el nombre del método no puede cruzar tablas.
    @Query("""
            SELECT h FROM Horario h
            WHERE h.localId = :localId
              AND h.profesionalId IN (SELECT p.id FROM Profesional p WHERE p.localId = :localId AND p.activo = true)
            """)
    List<Horario> buscarDeProfesionalesActivos(@Param("localId") UUID localId);

    // Las franjas de esos profesionales del local (para calcular sus horas libres).
    List<Horario> findByLocalIdAndProfesionalIdIn(UUID localId, Collection<UUID> profesionales);
}
