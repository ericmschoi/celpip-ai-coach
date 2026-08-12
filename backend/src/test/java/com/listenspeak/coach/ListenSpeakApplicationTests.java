package com.listenspeak.coach;

import static org.assertj.core.api.Assertions.assertThat;

import com.listenspeak.coach.platform.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ListenSpeakApplicationTests {

    @Autowired
    private AppProperties properties;

    @Test
    void contextLoadsWithSeedContentAndLocalStorage() {
        assertThat(properties.contentMode()).isEqualTo(AppProperties.ContentMode.SEED);
        assertThat(properties.storage().mode()).isEqualTo(AppProperties.StorageMode.LOCAL);
        assertThat(properties.openai().generationModel()).isNotBlank();
    }
}
