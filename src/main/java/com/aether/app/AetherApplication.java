package com.aether.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class AetherApplication {

    public static void main(String[] args) {
        SpringApplication.run(AetherApplication.class, args);
    }
}

