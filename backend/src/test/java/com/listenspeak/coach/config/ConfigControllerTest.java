package com.listenspeak.coach.config;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class ConfigControllerTest {

    private MockMvc mockMvc;

    @Autowired
    void setUp(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void exposesAllSixListeningPartsAndEightSpeakingTasks() throws Exception {
        mockMvc.perform(get("/api/v1/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listeningParts", hasSize(6)))
                .andExpect(jsonPath("$.speakingTasks", hasSize(8)))
                .andExpect(jsonPath("$.difficulties", hasSize(3)))
                .andExpect(jsonPath("$.contentMode").value("SEED"));
    }

    @Test
    void publishesTaskTimingsSoTheClientNeverHardCodesThem() throws Exception {
        mockMvc.perform(get("/api/v1/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speakingTasks[0].taskNumber").value(1))
                .andExpect(jsonPath("$.speakingTasks[0].preparationSeconds").value(30))
                .andExpect(jsonPath("$.speakingTasks[0].answerSeconds").value(90))
                .andExpect(jsonPath("$.speakingTasks[4].taskNumber").value(5))
                .andExpect(jsonPath("$.speakingTasks[4].preparationSeconds").value(60));
    }

    @Test
    void neverLeaksProviderOrInfrastructureSecrets() throws Exception {
        String body = mockMvc.perform(get("/api/v1/config"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("apiKey")
                .doesNotContain("openai")
                .doesNotContain("bucket")
                .doesNotContain("dynamodb");
    }
}
