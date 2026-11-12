package ar.com.slotly;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Reloj de los tests de integración: cada test fija la hora que necesita en su @BeforeEach con fijar(...).
// Mientras nadie lo fija, anda con la hora real, así no cambia nada para los tests que no lo usan.
public class RelojDeTest extends Clock {

    private Instant fijo;

    public void fijar(Instant instante) {
        this.fijo = instante;
    }

    @Override
    public Instant instant() {
        return fijo != null ? fijo : Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return Clock.fixed(instant(), zona);
    }

    // Lo registra @IntegrationTest. @Primary: le gana al reloj real de RelojConfig.
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        RelojDeTest relojDeTest() {
            return new RelojDeTest();
        }
    }
}
