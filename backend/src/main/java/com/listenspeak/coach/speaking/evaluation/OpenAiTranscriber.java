package com.listenspeak.coach.speaking.evaluation;

import com.listenspeak.coach.platform.config.AppProperties;
import com.listenspeak.coach.platform.openai.OpenAiConfiguredCondition;
import com.listenspeak.coach.platform.openai.OpenAiErrors;
import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import com.listenspeak.coach.speaking.evaluation.TranscriptionModels.Capability;
import com.openai.client.OpenAIClient;
import com.openai.models.audio.transcriptions.Transcription;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Produces the user-facing transcript.
 *
 * <p>The transcript is what the filler count, the repeated-start count, and the
 * word count are computed from. Transcription models default to producing
 * <em>readable</em> text: they drop "um", merge false starts, and tidy
 * self-corrections, which would silently empty exactly those measurements. So a
 * verbatim instruction is sent, and — where the model supports it — the filler
 * words themselves are passed as keywords.
 *
 * <p>Every parameter is gated on {@link TranscriptionModels}. Nothing
 * unsupported is sent speculatively: no {@code timestamp_granularities} and no
 * logprob request goes to {@code gpt-transcribe}, because that model supports
 * neither. Word timings come from {@link OpenAiWordTimingAnalyzer} instead.
 *
 * <p>The audio is never logged, and neither is the transcript: only its length,
 * the latency, and the usage.
 */
@Component
@Conditional(OpenAiConfiguredCondition.class)
public class OpenAiTranscriber implements Transcriber {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTranscriber.class);

    /**
     * A transcription prompt is a style hint: the model continues in the
     * register it establishes, so this is written as verbatim speech full of the
     * disfluencies that have to survive.
     */
    static final String VERBATIM_PROMPT =
            "Transcribe exactly what is said, word for word, including every filler and hesitation. "
                    + "Keep um, uh, er, mm, like, you know, I mean. Keep repeated words such as "
                    + "\"I I think\". Keep false starts and self-corrections such as "
                    + "\"I went to the— I mean, I visited the office\". Do not tidy, summarise, "
                    + "paraphrase, or remove anything.";

    /** Literal tokens the model should expect, so they are not smoothed away. */
    static final List<String> FILLER_KEYWORDS = List.of("um", "uh", "er", "erm", "mm", "like", "you know", "I mean");

    static final List<String> LANGUAGES = List.of("en");

    private final OpenAIClient client;
    private final AppProperties properties;

    public OpenAiTranscriber(OpenAIClient client, AppProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public TranscriptionResult transcribe(Path recording, String filename) {
        String model = properties.openai().transcriptionModel();
        TranscriptionCreateParams params = buildParams(model, recording);

        long startedAt = System.nanoTime();
        TranscriptionCreateResponse response;
        try {
            response = client.audio().transcriptions().create(params);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw OpenAiErrors.translate("transcription", e);
        }
        long latencyMillis = (System.nanoTime() - startedAt) / 1_000_000;

        Transcription body = response.transcription()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.PROVIDER_UNAVAILABLE, "The transcription service returned no text."));

        String text = body.text().trim();

        log.info(
                "Transcription ok model={} chars={} inputTokens={} outputTokens={} latencyMs={}",
                model,
                text.length(),
                inputTokens(body),
                outputTokens(body),
                latencyMillis);

        if (text.isBlank()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "No speech was detected in that recording. Check your microphone and try again.");
        }

        return new TranscriptionResult(
                text, model, "json", inputTokens(body), outputTokens(body), latencyMillis, true);
    }

    /** Builds a request containing only parameters this model documents support for. */
    TranscriptionCreateParams buildParams(String model, Path recording) {
        TranscriptionCreateParams.Builder params =
                TranscriptionCreateParams.builder().model(model).file(recording);

        if (TranscriptionModels.supports(model, Capability.PROMPT)) {
            params.prompt(VERBATIM_PROMPT);
        }
        if (TranscriptionModels.supports(model, Capability.KEYWORDS)) {
            params.keywords(FILLER_KEYWORDS);
        }
        if (TranscriptionModels.supports(model, Capability.LANGUAGES)) {
            params.languages(LANGUAGES);
        } else if (TranscriptionModels.supports(model, Capability.LANGUAGE_SINGULAR)) {
            params.language(LANGUAGES.get(0));
        }

        // Deliberately absent: response_format=verbose_json, timestamp_granularities,
        // and any logprob request. gpt-transcribe supports none of them; timings
        // come from the separate whisper-1 analysis instead.
        return params.build();
    }

    private static long inputTokens(Transcription body) {
        return body.usage()
                .filter(Transcription.Usage::isTokens)
                .map(usage -> usage.asTokens().inputTokens())
                .orElse(-1L);
    }

    private static long outputTokens(Transcription body) {
        return body.usage()
                .filter(Transcription.Usage::isTokens)
                .map(usage -> usage.asTokens().outputTokens())
                .orElse(-1L);
    }
}
