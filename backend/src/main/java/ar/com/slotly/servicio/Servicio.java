package ar.com.slotly.servicio;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Un servicio que ofrece un local (corte, barba...). Mapea la tabla servicio (V2). Por ahora solo lo que usa la home.
@Entity
@Table(name = "servicio")
public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // El local como id simple, sin @ManyToOne (doc 03, "Mapeo JPA").
    private UUID localId;

    private String nombre;
    private String descripcion;
    private int duracionMin;
    private int precio;
    private int orden;
    private boolean activo;

    protected Servicio() {
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

    public String getDescripcion() {
        return descripcion;
    }

    public int getDuracionMin() {
        return duracionMin;
    }

    public int getPrecio() {
        return precio;
    }

    public int getOrden() {
        return orden;
    }

    public boolean isActivo() {
        return activo;
    }

}