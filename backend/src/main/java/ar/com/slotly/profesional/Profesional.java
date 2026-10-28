package ar.com.slotly.profesional;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Un profesional de un local. Mapea la tabla profesional (V3). Por ahora solo lo que usa la home.
@Entity
@Table(name = "profesional")
public class Profesional {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID localId;

    private String nombre;
    private int orden;
    private boolean activo;

    protected Profesional() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getLocalId() {
        return localId;
    }

    public String getNombre() {
        return nombre;
    }

    public int getOrden() {
        return orden;
    }

    public boolean isActivo() {
        return activo;
    }

}
