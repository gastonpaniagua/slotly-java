package ar.com.slotly;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// Propiedades propias de Slotly (prefijo "slotly" en application.properties). Si falta alguna, la app no arranca.
// Los @Pattern también frenan una variable de entorno que falta: Spring deja el texto "${SLOTLY_…}" tal cual, y eso
// no es un dominio ni una dirección (Etapa 9).
@ConfigurationProperties(prefix = "slotly")
@Validated
public record SlotlyProperties(
        @NotBlank @Pattern(regexp = "[a-z0-9.-]+") String dominioBase,
        // La dirección pública de cada local, para los links de los mensajes (Etapa 8). {subdominio} se reemplaza
        // por el del local. En producción se pisa con SLOTLY_URL_SITIO (por ejemplo https://{subdominio}.slotly.com.ar).
        @NotBlank @Pattern(regexp = "https?://\\S+") String urlSitio,
        // Opcional. El local que se usa cuando el host no lleva el subdominio de uno activo. Sirve para la demo
        // publicada en un dominio sin subdominios por local (slotly-demo.onrender.com); vacío, un host así da 404.
        String localPorDefecto,
        // Solo la demo publicada (perfil demo): la pantalla del código lo muestra, porque quien prueba la demo no ve
        // el envío simulado. En un local real, siempre false.
        boolean demo
) {
}