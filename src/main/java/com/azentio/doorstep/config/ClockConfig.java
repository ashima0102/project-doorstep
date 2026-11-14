package com.azentio.doorstep.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Injected wherever "now" matters so tests can pin time with Clock.fixed. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
