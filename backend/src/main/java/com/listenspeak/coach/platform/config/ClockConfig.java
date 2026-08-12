package com.listenspeak.coach.platform.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A single injected clock so time-dependent behaviour - TTLs, daily caps,
 * signed-link expiry - can be tested without sleeping.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
