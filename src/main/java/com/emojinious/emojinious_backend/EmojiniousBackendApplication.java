package com.emojinious.emojinious_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EmojiniousBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(EmojiniousBackendApplication.class, args);
	}

}
