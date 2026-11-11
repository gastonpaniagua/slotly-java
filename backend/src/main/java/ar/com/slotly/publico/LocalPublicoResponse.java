package ar.com.slotly.publico;

import java.math.BigDecimal;

import ar.com.slotly.local.Local;

// Lo que ve cualquiera de un local en su home. Sin datos internos (CBU, activo, reglas de turnos).
public record LocalPublicoResponse(
        String nombre,
        String descripcion,
        String logoUrl,
        String colorAcento,
        String whatsapp,
        String instagram,
        String tiktok,
        String facebook,
        String direccion,
        String ciudad,
        BigDecimal latitud,
        BigDecimal longitud
) {

    // Arma la respuesta a partir de la entidad.
    static LocalPublicoResponse desde(Local local) {
        return new LocalPublicoResponse(
                local.getNombre(),
                local.getDescripcion(),
                local.getLogoUrl(),
                local.getColorAcento(),
                local.getWhatsapp(),
                local.getInstagram(),
                local.getTiktok(),
                local.getFacebook(),
                local.getDireccion(),
                local.getCiudad(),
                local.getLatitud(),
                local.getLongitud());
    }
}
