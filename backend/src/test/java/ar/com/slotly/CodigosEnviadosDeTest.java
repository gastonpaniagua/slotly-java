package ar.com.slotly;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import ar.com.slotly.reserva.EnvioDeCodigos;

// El "envío" de códigos de los tests de integración (HU-22): en vez de escribir en la terminal, guarda el último
// código de cada teléfono para que el test lo pueda usar, y sabe si a un teléfono no se le mandó nada (CA2).
public class CodigosEnviadosDeTest implements EnvioDeCodigos {

    private final Map<String, String> ultimos = new ConcurrentHashMap<>();

    @Override
    public void enviar(String telefono, String codigo) {
        ultimos.put(telefono, codigo);
    }

    public Optional<String> ultimoPara(String telefono) {
        return Optional.ofNullable(ultimos.get(telefono));
    }

    public void reiniciar() {
        ultimos.clear();
    }

    // Lo registra @IntegrationTest. @Primary: le gana al envío simulado.
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        CodigosEnviadosDeTest codigosEnviadosDeTest() {
            return new CodigosEnviadosDeTest();
        }
    }
}
