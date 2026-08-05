package com.finpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * FinPilot backend entrypoint.
 *
 * Run locally (once mvn dependencies are resolvable):
 *     mvn spring-boot:run-
 *
 * Requires Postgres reachable at the URL in application.yml, and Flyway
 * migrations (V1, V2 as of Phase 1) applied automatically on startup since
 * spring.flyway.enabled=true.
 */
@SpringBootApplication
public class FinPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinPilotApplication.class, args);
    }

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

//    @Bean
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);
    }
}
