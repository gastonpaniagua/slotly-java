package ar.com.slotly;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// El "ahora" de la app sale de este reloj y nunca de Instant.now() (doc 06, "Reloj controlado").
// Los tests lo reemplazan por uno que se puede fijar, así prueban "son las 10:20" sin depender de la hora real.
@Configuration
public class RelojConfig {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
