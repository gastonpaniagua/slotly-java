package ar.com.slotly.publico;

import java.math.BigDecimal;
import java.util.List;

import ar.com.slotly.local.Local;
import ar.com.slotly.profesional.Horario;
import ar.com.slotly.servicio.Servicio;

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
        BigDecimal longitud,
        List<ServicioPublicoResponse> servicios,
        List<DiaPublicoResponse> horarios
) {

    // Arma la respuesta a partir de la entidad, sus servicios visibles (ya filtrados y ordenados)
    // y las franjas de sus profesionales activos (se juntan en el horario de atención).
    static LocalPublicoResponse desde(Local local, List<Servicio> servicios, List<Horario> franjas) {
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
                local.getLongitud(),
                servicios.stream().map(ServicioPublicoResponse::desde).toList(),
                HorarioDeAtencion.calcular(franjas));
    }
}
