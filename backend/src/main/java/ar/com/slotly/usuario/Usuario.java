package ar.com.slotly.usuario;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Un usuario del panel (V8): el dueño (admin) o un profesional. Solo lectura por ahora: se crean en la Etapa 7.
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    private UUID id;
    private UUID localId;
    private String email;
    private String passwordHash;
    private String rol;
    private UUID profesionalId;
    private boolean activo;

    protected Usuario() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getLocalId() {
        return localId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRol() {
        return rol;
    }

    public UUID getProfesionalId() {
        return profesionalId;
    }

    public boolean isActivo() {
        return activo;
    }
}
