package ar.com.slotly.turno;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Turnos de un local. Siempre se filtra por localId (HU-04).
public interface TurnoRepository extends JpaRepository<Turno, UUID> {

    // Todo lo que ocupa a esos profesionales entre desde y hasta: turnos, bloqueos y apartados vigentes.
    // No cuentan los cancelados ni los vencidos, ni los apartados que ya vencieron aunque sigan como 'apartado'
    // (el trabajo que los marca como vencidos llega en la Etapa 8; doc 03, "Apartados vencidos").
    // Se cruzan con el rango si empiezan antes de que termine y terminan (con limpieza) después de que empiece.
    @Query("""
            SELECT t FROM Turno t
            WHERE t.localId = :localId
              AND t.profesionalId IN :profesionales
              AND t.estado NOT IN ('cancelado', 'vencido')
              AND NOT (t.estado = 'apartado' AND t.venceEn <= :ahora)
              AND t.inicio < :hasta
              AND t.ocupadoHasta > :desde
            """)
    List<Turno> buscarOcupaciones(@Param("localId") UUID localId,
            @Param("profesionales") Collection<UUID> profesionales,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta, @Param("ahora") Instant ahora);
}
