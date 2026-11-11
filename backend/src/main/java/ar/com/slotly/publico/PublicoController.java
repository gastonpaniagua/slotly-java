package ar.com.slotly.publico;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.profesional.Horario;
import ar.com.slotly.profesional.HorarioRepository;
import ar.com.slotly.servicio.Servicio;
import ar.com.slotly.servicio.ServicioRepository;

// Endpoints públicos de la home del local. El local sale del Host (demo.localhost:5173 → demo).
@RestController
@RequestMapping("/api/publico")
class PublicoController {

    private final LocalResolver localResolver;
    private final ServicioRepository servicioRepository;
    private final HorarioRepository horarioRepository;

    PublicoController(LocalResolver localResolver, ServicioRepository servicioRepository,
            HorarioRepository horarioRepository) {
        this.localResolver = localResolver;
        this.servicioRepository = servicioRepository;
        this.horarioRepository = horarioRepository;
    }

    // El Host lo pone el navegador (no se puede elegir a mano), por eso se oculta en la documentación.
    @GetMapping("/local")
    @Operation(summary = "Datos de la home del local que corresponde al Host: marca, servicios y horario de atención")
    LocalPublicoResponse local(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host) {
        Local local = localResolver.resolver(host);
        List<Servicio> servicios = servicioRepository.findByLocalIdAndActivoTrueOrderByOrdenAscNombreAsc(local.getId());
        List<Horario> franjas = horarioRepository.buscarDeProfesionalesActivos(local.getId());
        return LocalPublicoResponse.desde(local, servicios, franjas);
    }
}
