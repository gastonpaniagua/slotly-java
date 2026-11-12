package ar.com.slotly.local;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Un local (peluquería o barbería) de Slotly. Mapea la tabla local (V1). Por ahora solo lo que usa la home.
@Entity
@Table(name = "local")
public class Local {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nombre;
    private String subdominio;
    private String zonaHoraria;
    private boolean activo;

    // Marca
    private String descripcion;
    private String logoUrl;
    private String colorAcento;

    // Contacto
    private String whatsapp;
    private String instagram;
    private String tiktok;
    private String facebook;

    // Ubicación
    private String direccion;
    private String ciudad;
    private BigDecimal latitud;
    private BigDecimal longitud;

    // Reglas de turnos (doc 03, valores por defecto en la base)
    private int intervaloMin;
    private int limpiezaMin;
    private int anticipacionMin;
    private int topeDias;
    private int apartadoMin;
    private int maxTurnosFuturos;

    // Pago por transferencia (se muestra solo en la reserva que elige transferencia, HU-10 CA5)
    private String alias;
    private String titularCuenta;
    private String cbu;

    // JPA necesita un constructor sin argumentos. Protected: solo lo usa Hibernate.
    protected Local() {
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getSubdominio() {
        return subdominio;
    }

    public String getZonaHoraria() {
        return zonaHoraria;
    }

    public boolean isActivo() {
        return activo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public String getColorAcento() {
        return colorAcento;
    }

    public String getWhatsapp() {
        return whatsapp;
    }

    public String getInstagram() {
        return instagram;
    }

    public String getTiktok() {
        return tiktok;
    }

    public String getFacebook() {
        return facebook;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getCiudad() {
        return ciudad;
    }

    public BigDecimal getLatitud() {
        return latitud;
    }

    public BigDecimal getLongitud() {
        return longitud;
    }

    // Cada cuántos minutos se ofrece un horario de inicio.
    public int getIntervaloMin() {
        return intervaloMin;
    }

    // Minutos libres después de cada turno (un servicio puede tener la suya).
    public int getLimpiezaMin() {
        return limpiezaMin;
    }

    // No se puede reservar para dentro de menos de estos minutos.
    public int getAnticipacionMin() {
        return anticipacionMin;
    }

    // Hasta cuántos días hacia adelante se reserva.
    public int getTopeDias() {
        return topeDias;
    }

    // Cuánto dura el apartado del horario mientras el cliente completa sus datos.
    public int getApartadoMin() {
        return apartadoMin;
    }

    // Cuántos turnos futuros puede tener un mismo teléfono en este local (HU-14).
    public int getMaxTurnosFuturos() {
        return maxTurnosFuturos;
    }

    public String getAlias() {
        return alias;
    }

    public String getTitularCuenta() {
        return titularCuenta;
    }

    public String getCbu() {
        return cbu;
    }
}
