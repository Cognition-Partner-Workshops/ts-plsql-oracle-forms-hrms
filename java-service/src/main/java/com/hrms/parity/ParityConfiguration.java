package com.hrms.parity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("parity")
public class ParityConfiguration {
    @Bean
    Clock parityClock() {
        return Clock.fixed(Instant.parse("2024-01-31T12:00:00Z"), ZoneOffset.UTC);
    }

    @Bean
    FlywayMigrationStrategy freshParityDatabase() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
