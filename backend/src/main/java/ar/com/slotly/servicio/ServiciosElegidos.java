package ar.com.slotly.servicio;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ar.com.slotly.error.SlotlyException;

// Valida los servicios que eligió el cliente: al menos uno, y todos activos y de este local.
// Si alguno es de otro local, está oculto o no existe, responde siempre lo mismo (404), sin confirmar
// que existe en otro lado (HU-06 CA4, HU-07 CA11, HU-04 CA3).
@Component
public class ServiciosElegidos {

    private final ServicioRepository servicioRepository;

    ServiciosElegidos(ServicioRepository servicioRepository) {
        this.servicioRepository = servicioRepository;
    }

    public List<Servicio> validar(UUID localId, Set<UUID> ids) {
        if (ids.isEmpty()) {
            throw new SlotlyException(HttpStatus.BAD_REQUEST, "VALIDACION", "Elegí al menos un servicio.");
        }
        List<Servicio> servicios = servicioRepository.findByLocalIdAndActivoTrueAndIdIn(localId, ids);
        if (servicios.size() != ids.size()) {
            throw new SlotlyException(HttpStatus.NOT_FOUND, "SERVICIO_NO_ENCONTRADO",
                    "Alguno de los servicios elegidos no existe en este local.");
        }
        return servicios;
    }
}
