package com.flightprovider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FlightProviderApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlightProviderApplication.class, args);
    }
}
