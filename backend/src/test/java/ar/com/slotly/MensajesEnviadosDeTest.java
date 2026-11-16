package ar.com.slotly;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import ar.com.slotly.mensaje.EnvioDeMensajes;

// El "envío" de mensajes de los tests de integración (Etapa 8): guarda lo que se mandó para que el test lo revise, y
// puede fallar a propósito las próximas N veces para probar los reintentos (HU-35 CA3).
public class MensajesEnviadosDeTest implements EnvioDeMensajes {

    public record Enviado(String telefono, String texto) {
    }

    private final List<Enviado> enviados = new CopyOnWriteArrayList<>();
    private final AtomicInteger fallasPendientes = new AtomicInteger();

    @Override
    public void enviar(String telefono, String texto) {
        if (fallasPendientes.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new IllegalStateException("El proveedor no respondió");
        }
        enviados.add(new Enviado(telefono, texto));
    }

    public List<Enviado> enviados() {
        return List.copyOf(enviados);
    }

    public void fallarLasProximas(int veces) {
        fallasPendientes.set(veces);
    }

    public void reiniciar() {
        enviados.clear();
        fallasPendientes.set(0);
    }

    // Lo registra @IntegrationTest. @Primary: le gana al envío simulado.
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        MensajesEnviadosDeTest mensajesEnviadosDeTest() {
            return new MensajesEnviadosDeTest();
        }
    }
}
