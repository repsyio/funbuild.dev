package dev.funbuild;

import io.repsy.core.response.services.RestResponseFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FunbuildApplication {

	@Bean
	RestResponseFactory restResponseFactory(MessageSource messageSource) {
		return new RestResponseFactory(messageSource);
	}

	public static void main(String[] args) {
		SpringApplication.run(FunbuildApplication.class, args);
	}

}
