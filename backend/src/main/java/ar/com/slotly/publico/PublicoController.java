package ar.com.slotly.publico;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.servicio.Servicio;
import ar.com.slotly.servicio.ServicioRepository;

// Endpoints públicos de la home del local. El local sale del Host (demo.localhost:5173 → demo).
@RestController
@RequestMapping("/api/publico")
class PublicoController {

    private final LocalResolver localResolver;
    private final ServicioRepository servicioRepository;

    PublicoController(LocalResolver localResolver, ServicioRepository servicioRepository) {
        this.localResolver = localResolver;
        this.servicioRepository = servicioRepository;
    }

    @GetMapping("/local")
    LocalPublicoResponse local(@RequestHeader(HttpHeaders.HOST) String host) {
        Local local = localResolver.resolver(host);
        List<Servicio> servicios = servicioRepository.findByLocalIdAndActivoTrueOrderByOrdenAscNombreAsc(local.getId());
        return LocalPublicoResponse.desde(local, servicios);
    }
}
