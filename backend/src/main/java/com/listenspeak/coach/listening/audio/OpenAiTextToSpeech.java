package com.listenspeak.coach.listening.audio;

import com.listenspeak.coach.platform.config.AppProperties;
import com.listenspeak.coach.platform.openai.OpenAiConfiguredCondition;
import com.listenspeak.coach.platform.openai.OpenAiErrors;
import com.openai.client.OpenAIClient;
import com.openai.core.http.HttpResponse;
import com.openai.models.audio.speech.SpeechCreateParams;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * OpenAI text-to-speech for one dialogue turn.
 *
 * <p>WAV is requested for the intermediate segments so the assembler joins
 * uncompressed audio and only encodes once, at the end.
 */
@Component
@Conditional(OpenAiConfiguredCondition.class)
public class OpenAiTextToSpeech implements TextToSpeech {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTextToSpeech.class);

    /**
     * Delivery direction for every turn. It asks for a clean studio recording
     * with no added production, because sound effects, music, or a spoken
     * speaker name would all break the exercise.
     */
    private static final String INSTRUCTIONS =
            """
            Speak in natural Canadian English at a normal conversational pace, as if \
            recorded in a quiet studio with a good microphone. Sound like a real person \
            talking to someone in the room, not like a narrator reading aloud. \
            Do not add music, sound effects, background noise, hum, ringing, echo, or \
            any introduction. Do not announce or read out any speaker name, label, or \
            stage direction. Speak only the words given.
            """;

    private final OpenAIClient client;
    private final AppProperties properties;

    public OpenAiTextToSpeech(OpenAIClient client, AppProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public byte[] synthesize(String text, String voice) {
        SpeechCreateParams params = SpeechCreateParams.builder()
                .model(properties.openai().ttsModel())
                .voice(voice)
                .input(text)
                .instructions(INSTRUCTIONS)
                .responseFormat(SpeechCreateParams.ResponseFormat.WAV)
                .build();

        long startedAt = System.nanoTime();
        try (HttpResponse response = client.audio().speech().create(params)) {
            byte[] wav = response.body().readAllBytes();

            // Log shape and latency, never the text and never the audio.
            log.debug(
                    "TTS ok voice={} chars={} bytes={} latencyMs={}",
                    voice,
                    text.length(),
                    wav.length,
                    (System.nanoTime() - startedAt) / 1_000_000);

            if (wav.length == 0) {
                throw OpenAiErrors.translate("text-to-speech", new IllegalStateException("empty audio"));
            }
            return wav;

        } catch (IOException e) {
            throw OpenAiErrors.translate("text-to-speech", e);
        } catch (RuntimeException e) {
            throw OpenAiErrors.translate("text-to-speech", e);
        }
    }
}
