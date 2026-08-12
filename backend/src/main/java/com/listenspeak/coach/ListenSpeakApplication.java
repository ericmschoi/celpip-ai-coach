package com.listenspeak.coach;

import com.listenspeak.coach.platform.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class ListenSpeakApplication {

    public static void main(String[] args) {
        SpringApplication.run(ListenSpeakApplication.class, args);
    }
}
