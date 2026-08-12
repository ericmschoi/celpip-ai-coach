package com.listenspeak.coach.platform.openai;

import com.listenspeak.coach.platform.config.AppProperties;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAI client exists only when a key is configured, so a misconfigured
 * deployment fails with a clear application error instead of an obscure
 * authentication failure on the first user request.
 */
@Configuration
@Conditional(OpenAiConfiguredCondition.class)
public class OpenAiClientConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClientConfig.class);

    @Bean
    OpenAIClient openAiClient(AppProperties properties) {
        AppProperties.OpenAi openai = properties.openai();

        // Model names, timeout, and retry budget all come from configuration.
        // Nothing about the provider is hard-coded in an adapter.
        log.info(
                "OpenAI client enabled. generation={} scoring={} tts={} transcription={} timeout={} maxRetries={}",
                openai.generationModel(),
                openai.scoringModel(),
                openai.ttsModel(),
                openai.transcriptionModel(),
                openai.requestTimeout(),
                openai.maxRetries());

        return OpenAIOkHttpClient.builder()
                .apiKey(openai.apiKey())
                .baseUrl(openai.baseUrl())
                .timeout(openai.requestTimeout())
                // The SDK's own retry budget, which backs off with jitter and
                // only retries the status codes that are worth retrying.
                .maxRetries(openai.maxRetries())
                .build();
    }
}
