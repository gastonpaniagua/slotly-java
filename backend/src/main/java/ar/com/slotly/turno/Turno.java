package ar.com.slotly.turno;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Un turno, bloqueo o apartado (todo en la misma tabla, doc 03). Mapea la tabla turno (V6).
// Por ahora solo lo que usa el cálculo de horas libres; la reserva (Etapa 4) suma el resto.
@Entity
@Table(name = "turno")
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID localId;
    private UUID profesionalId;

    // 'reserva', 'manual' o 'bloqueo'
    private String tipo;
    // 'apartado', 'confirmado', 'cancelado', 'vencido', 'completado' o 'ausente'
    private String estado;

    // En UTC. El profesional está ocupado de inicio a ocupadoHasta (fin + limpieza).
    private Instant inicio;
    private Instant fin;
    private Instant ocupadoHasta;
    // Solo en los apartados: cuándo deja de bloquear el horario.
    private Instant venceEn;

    protected Turno() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getLocalId() {
        return localId;
    }

    public UUID getProfesionalId() {
        return profesionalId;
    }

    public String getTipo() {
        return tipo;
    }

    public String getEstado() {
        return estado;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFin() {
        return fin;
    }

    public Instant getOcupadoHasta() {
        return ocupadoHasta;
    }

    public Instant getVenceEn() {
        return venceEn;
    }
}
