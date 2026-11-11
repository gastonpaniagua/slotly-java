package ar.com.slotly.publico;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;

// Endpoints públicos de la home del local. El local sale del Host (demo.localhost:5173 → demo).
@RestController
@RequestMapping("/api/publico")
class PublicoController {

    private final LocalResolver localResolver;

    PublicoController(LocalResolver localResolver) {
        this.localResolver = localResolver;
    }

    @GetMapping("/local")
    LocalPublicoResponse local(@RequestHeader(HttpHeaders.HOST) String host) {
        Local local = localResolver.resolver(host);
        return LocalPublicoResponse.desde(local);
    }
}
