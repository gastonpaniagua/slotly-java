package ar.com.slotly.local;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

// Saca el subdominio del header Host (demo.localhost:5173 → demo).
// Si el host no sirve devuelve vacío, nunca tira error: el 404 lo decide quien lo usa.
class SubdominioExtractor {

    // Mismo patrón que el CHECK de local.subdominio (V1): una sola regla para la base y la API.
    private static final Pattern SUBDOMINIO = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");

    // Lo que tiene que haber al final del host: "." + dominio base (".localhost").
    private final String sufijo;

    SubdominioExtractor(String dominioBase) {
        this.sufijo = "." + dominioBase.toLowerCase(Locale.ROOT);
    }

    Optional<String> extraer(String host) {
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }

        String sinPuerto = host.toLowerCase(Locale.ROOT).split(":")[0];
        if (!sinPuerto.endsWith(sufijo)) {
            return Optional.empty();
        }

        String candidato = sinPuerto.substring(0, sinPuerto.length() - sufijo.length());
        if (!SUBDOMINIO.matcher(candidato).matches()) {
            return Optional.empty();
        }
        return Optional.of(candidato);
    }
}