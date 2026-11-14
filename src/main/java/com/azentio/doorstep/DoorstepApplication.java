package com.azentio.doorstep;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DoorstepApplication {

    public static void main(String[] args) {
        SpringApplication.run(DoorstepApplication.class, args);
    }
}
