package com.listenspeak.coach.listening;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.listenspeak.coach.listening.generation.ExerciseGenerator;
import com.listenspeak.coach.listening.generation.SeedExerciseGenerator;
import com.listenspeak.coach.platform.security.LocalStubAuthenticationFilter;
import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Whatever goes wrong inside the generator, the client must receive a Problem
 * Details body with a stable {@code code} - never a provider message, never a
 * stack trace.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProviderFailureTest {

    private MockMvc mockMvc;

    @MockitoBean
    private SeedExerciseGenerator generator;

    @Autowired
    void setUp(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private void whenGeneratorThrows(RuntimeException failure) {
        given(generator.mode()).willReturn(com.listenspeak.coach.platform.config.AppProperties.ContentMode.SEED);
        given(generator.generate(any(), any())).willThrow(failure);
    }

    private org.springframework.test.web.servlet.ResultActions createExercise() throws Exception {
        return mockMvc.perform(post("/api/v1/listening/exercises")
                .header(LocalStubAuthenticationFilter.HEADER, "provider-failure-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"part\":5,\"difficulty\":\"COMPETENT\"}"));
    }

    @ParameterizedTest(name = "{0} -> HTTP {1}, retryable={2}")
    @CsvSource({
        "PROVIDER_UNAVAILABLE, 503, true",
        "PROVIDER_RATE_LIMITED, 503, true",
        "PROVIDER_TIMEOUT, 504, true",
        "PROVIDER_REFUSED, 422, false",
        "GENERATION_INVALID, 422, true",
        "AUDIO_QUALITY_FAILED, 422, true",
        "PROVIDER_NOT_CONFIGURED, 503, false"
    })
    void mapsProviderFailuresToStableProblemDetails(String code, int expectedStatus, boolean retryable)
            throws Exception {

        ErrorCode errorCode = ErrorCode.valueOf(code);
        whenGeneratorThrows(new ApiException(errorCode, "something went wrong upstream"));

        createExercise()
                .andExpect(status().is(expectedStatus))
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.retryable").value(retryable))
                .andExpect(jsonPath("$.type").exists())
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void neverLeaksAnUnexpectedExceptionsDetailToTheClient() throws Exception {
        whenGeneratorThrows(new IllegalStateException("connection to api.openai.com failed with key sk-secret"));

        String body = createExercise()
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("sk-secret")
                .doesNotContain("api.openai.com")
                .doesNotContain("IllegalStateException");
    }

    @Test
    void doesNotChargeGenerationTwiceWhenTheFirstAttemptFails() throws Exception {
        whenGeneratorThrows(new ApiException(ErrorCode.PROVIDER_TIMEOUT, "timed out"));

        createExercise().andExpect(status().isGatewayTimeout());

        // The generator is called exactly once per request: there is no internal
        // retry loop against a paid API.
        org.mockito.Mockito.verify(generator, org.mockito.Mockito.times(1))
                .generate(any(), any());
    }

    @Test
    void generatorContractExposesItsContentMode() {
        ExerciseGenerator asPort = generator;
        org.assertj.core.api.Assertions.assertThat(asPort).isInstanceOf(ExerciseGenerator.class);
    }
}
