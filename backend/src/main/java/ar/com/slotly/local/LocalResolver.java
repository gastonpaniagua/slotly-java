package ar.com.slotly.local;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ar.com.slotly.SlotlyProperties;
import ar.com.slotly.error.SlotlyException;

// Encuentra el local a partir del header Host (demo.localhost:5173 → Local "demo").
// Si no hay uno activo para ese host tira LOCAL_NO_ENCONTRADO (404): host raro, inexistente o inactivo dan
// la misma respuesta, así nadie puede averiguar qué subdominios existen.
@Component
public class LocalResolver {

    private final LocalRepository localRepository;
    private final SubdominioExtractor subdominioExtractor;

    public LocalResolver(LocalRepository localRepository, SlotlyProperties propiedades) {
        this.localRepository = localRepository;
        this.subdominioExtractor = new SubdominioExtractor(propiedades.dominioBase());
    }

    public Local resolver(String host) {
        return subdominioExtractor.extraer(host)
                .flatMap(subdominio -> localRepository.findBySubdominioAndActivoTrue(subdominio))
                .orElseThrow(() -> new SlotlyException(HttpStatus.NOT_FOUND, "LOCAL_NO_ENCONTRADO",
                        "No existe un local con ese subdominio."));
    }
}
