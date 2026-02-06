package com.deskit.deskit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DeskitApplication {

	public static void main(String[] args) {
		SpringApplication.run(DeskitApplication.class, args);
	}

}
