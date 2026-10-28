package ar.com.slotly.profesional;

import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Una franja del horario semanal de un profesional, en hora del local. Mapea la tabla horario (V4).
@Entity
@Table(name = "horario")
public class Horario {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID localId;
    private UUID profesionalId;

    private int diaSemana;
    private LocalTime horaInicio;
    private LocalTime horaFin;

    protected Horario() {
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

    public int getDiaSemana() {
        return diaSemana;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }
}