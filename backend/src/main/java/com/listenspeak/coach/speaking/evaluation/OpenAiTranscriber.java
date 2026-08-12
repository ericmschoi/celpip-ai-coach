package com.listenspeak.coach.speaking.evaluation;

import com.listenspeak.coach.platform.config.AppProperties;
import com.listenspeak.coach.platform.openai.OpenAiConfiguredCondition;
import com.listenspeak.coach.platform.openai.OpenAiErrors;
import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import com.openai.client.OpenAIClient;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Transcribes a completed recording.
 *
 * <p>The audio itself is never logged, and neither is the transcript: only its
 * length and the latency of the call.
 */
@Component
@Conditional(OpenAiConfiguredCondition.class)
public class OpenAiTranscriber implements Transcriber {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTranscriber.class);

    private final OpenAIClient client;
    private final AppProperties properties;

    public OpenAiTranscriber(OpenAIClient client, AppProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String transcribe(Path recording, String filename) {
        TranscriptionCreateParams params = TranscriptionCreateParams.builder()
                .model(properties.openai().transcriptionModel())
                .file(recording)
                .build();

        long startedAt = System.nanoTime();
        try {
            TranscriptionCreateResponse response =
                    client.audio().transcriptions().create(params);

            String text = response.transcription()
                    .map(transcription -> transcription.text())
                    .orElseThrow(() -> new ApiException(
                            ErrorCode.PROVIDER_UNAVAILABLE, "The transcription service returned no text."));

            log.info(
                    "Transcription ok chars={} latencyMs={}",
                    text.length(),
                    (System.nanoTime() - startedAt) / 1_000_000);

            if (text.isBlank()) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "No speech was detected in that recording. Check your microphone and try again.");
            }
            return text.trim();

        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw OpenAiErrors.translate("transcription", e);
        }
    }
}
