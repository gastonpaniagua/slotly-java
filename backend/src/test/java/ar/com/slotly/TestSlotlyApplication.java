package ar.com.slotly;

import org.springframework.boot.SpringApplication;

public class TestSlotlyApplication {

	public static void main(String[] args) {
		SpringApplication.from(SlotlyApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
