package ar.com.slotly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
// Los trabajos programados (Etapa 8) quedan apagados: cada test los llama directo, con el reloj fijado.
@SpringBootTest(properties = "slotly.trabajos.activos=false")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojDeTest.Config.class, CodigosEnviadosDeTest.Config.class,
        MensajesEnviadosDeTest.Config.class})
public @interface IntegrationTest {
}