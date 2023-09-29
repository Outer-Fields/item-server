package io.mindspice.itemserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;


@SpringBootApplication
public class OkraItemServerApplication {

	public static void main(String[] args) throws IOException {
		SpringApplication.run(OkraItemServerApplication.class, args);
	}

}
