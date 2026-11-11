package ar.com.slotly.publico;

import java.util.UUID;

import ar.com.slotly.servicio.Servicio;

// Un servicio tal como lo ve el cliente en la home. El precio va en pesos enteros; el formato ($ 8.000) lo pone el front.
public record ServicioPublicoResponse(
        UUID id,
        String nombre,
        String descripcion,
        int duracionMin,
        int precio
) {

    static ServicioPublicoResponse desde(Servicio servicio) {
        return new ServicioPublicoResponse(
                servicio.getId(),
                servicio.getNombre(),
                servicio.getDescripcion(),
                servicio.getDuracionMin(),
                servicio.getPrecio());
    }
}
