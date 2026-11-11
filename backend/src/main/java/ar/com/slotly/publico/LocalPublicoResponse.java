package ar.com.slotly.publico;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import ar.com.slotly.local.Local;
import ar.com.slotly.profesional.Horario;
import ar.com.slotly.servicio.Servicio;

// Lo que ve cualquiera de un local en su home. Sin datos internos (CBU, activo, reglas de turnos).
// Los campos opcionales del local llevan @Schema con "null": así el tipo generado del front avisa que pueden faltar.
public record LocalPublicoResponse(
        String nombre,
        @Schema(types = {"string", "null"}) String descripcion,
        @Schema(types = {"string", "null"}) String logoUrl,
        @Schema(types = {"string", "null"}) String colorAcento,
        @Schema(types = {"string", "null"}) String whatsapp,
        @Schema(types = {"string", "null"}) String instagram,
        @Schema(types = {"string", "null"}) String tiktok,
        @Schema(types = {"string", "null"}) String facebook,
        @Schema(types = {"string", "null"}) String direccion,
        @Schema(types = {"string", "null"}) String ciudad,
        @Schema(types = {"number", "null"}) BigDecimal latitud,
        @Schema(types = {"number", "null"}) BigDecimal longitud,
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
