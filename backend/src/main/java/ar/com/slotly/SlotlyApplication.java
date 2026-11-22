package ar.com.slotly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Sin el usuario de prueba que Spring Security arma solo (y cuya contraseña escribe en el log al arrancar): el login
// del panel es propio (IngresoService) y no lo usa.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class SlotlyApplication {
	
	public static void main(String[] args) {
		SpringApplication.run(SlotlyApplication.class, args);
	}

}
