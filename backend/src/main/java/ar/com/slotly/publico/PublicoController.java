package ar.com.slotly.publico;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.profesional.Horario;
import ar.com.slotly.profesional.HorarioRepository;
import ar.com.slotly.profesional.ProfesionalRepository;
import ar.com.slotly.servicio.Servicio;
import ar.com.slotly.servicio.ServicioRepository;

// Endpoints públicos de la home y de la reserva. El local sale del Host (demo.localhost:5173 → demo).
// El Host lo pone el navegador (no se puede elegir a mano), por eso se oculta en la documentación.
@RestController
@RequestMapping("/api/publico")
class PublicoController {

    private final LocalResolver localResolver;
    private final ServicioRepository servicioRepository;
    private final HorarioRepository horarioRepository;
    private final ProfesionalRepository profesionalRepository;

    PublicoController(LocalResolver localResolver, ServicioRepository servicioRepository,
            HorarioRepository horarioRepository, ProfesionalRepository profesionalRepository) {
        this.localResolver = localResolver;
        this.servicioRepository = servicioRepository;
        this.horarioRepository = horarioRepository;
        this.profesionalRepository = profesionalRepository;
    }

    @GetMapping("/local")
    @Operation(summary = "Datos de la home del local que corresponde al Host: marca, servicios y horario de atención")
    LocalPublicoResponse local(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host) {
        Local local = localResolver.resolver(host);
        List<Servicio> servicios = servicioRepository.findByLocalIdAndActivoTrueOrderByOrdenAscNombreAsc(local.getId());
        List<Horario> franjas = horarioRepository.buscarDeProfesionalesActivos(local.getId());
        return LocalPublicoResponse.desde(local, servicios, franjas);
    }

    @GetMapping("/profesionales")
    @Operation(summary = "Profesionales activos del local que hacen todos los servicios elegidos (paso 2 de la reserva)")
    List<ProfesionalPublicoResponse> profesionales(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @RequestParam Set<UUID> servicios) {
        Local local = localResolver.resolver(host);
        if (servicios.isEmpty()) {
            throw new SlotlyException(HttpStatus.BAD_REQUEST, "VALIDACION", "Elegí al menos un servicio.");
        }
        // Todos los servicios tienen que ser activos y de este local. Si alguno es de otro local, está oculto
        // o no existe, la respuesta es la misma: 404, sin confirmar que existe en otro lado (HU-06 CA4, HU-04 CA3).
        if (servicioRepository.countByLocalIdAndActivoTrueAndIdIn(local.getId(), servicios) != servicios.size()) {
            throw new SlotlyException(HttpStatus.NOT_FOUND, "SERVICIO_NO_ENCONTRADO",
                    "Alguno de los servicios elegidos no existe en este local.");
        }
        return profesionalRepository.buscarActivosQueHacenTodos(local.getId(), servicios, servicios.size()).stream()
                .map(ProfesionalPublicoResponse::desde)
                .toList();
    }
}
