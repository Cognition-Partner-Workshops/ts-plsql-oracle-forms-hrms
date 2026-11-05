package com.hrms.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class CommonConfiguration {
    @Bean
    @Profile("!parity")
    Clock commonClock() {
        return Clock.systemDefaultZone();
    }
}
