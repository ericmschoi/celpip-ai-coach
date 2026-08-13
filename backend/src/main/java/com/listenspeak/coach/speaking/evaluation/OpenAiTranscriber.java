package com.listenspeak.coach.speaking.evaluation;

import com.listenspeak.coach.platform.config.AppProperties;
import com.listenspeak.coach.platform.openai.OpenAiConfiguredCondition;
import com.listenspeak.coach.platform.openai.OpenAiErrors;
import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import com.openai.client.OpenAIClient;
import com.openai.models.audio.AudioResponseFormat;
import com.openai.models.audio.transcriptions.Transcription;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import com.openai.models.audio.transcriptions.TranscriptionVerbose;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Transcribes a completed recording.
 *
 * <p>Two things matter here beyond getting text back.
 *
 * <p>First, the transcript is what the filler count, the repeated-start count,
 * and the pace are computed from. Transcription models default to producing
 * <em>readable</em> text: they drop "um", merge false starts, and tidy
 * self-corrections. That would silently zero exactly the measurements this app
 * reports, so a verbatim instruction is sent explicitly.
 *
 * <p>Second, word timestamps and confidence are requested when the model
 * supports them. If it rejects the richer response format, the call is retried
 * once as plain text rather than failing, and the result records which format
 * was actually used so the live report can state it.
 *
 * <p>The audio itself is never logged, and neither is the transcript: only its
 * length, the latency, and the usage.
 */
@Component
@Conditional(OpenAiConfiguredCondition.class)
public class OpenAiTranscriber implements Transcriber {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTranscriber.class);

    /**
     * Sent as the transcription prompt. A transcription prompt is a style hint:
     * the model continues in the register it establishes, so it is written as a
     * verbatim fragment full of the disfluencies that must survive.
     */
    static final String VERBATIM_PROMPT =
            "Transcribe exactly what is said, word for word, including every filler and hesitation. "
                    + "Keep um, uh, er, mm, like, you know, I mean. Keep repeated words such as "
                    + "\"I I think\". Keep false starts and self-corrections such as "
                    + "\"I went to the— I mean, I visited the office\". Do not tidy, summarise, "
                    + "paraphrase, or remove anything. Do not add punctuation that changes the words.";

    private final OpenAIClient client;
    private final AppProperties properties;

    public OpenAiTranscriber(OpenAIClient client, AppProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public TranscriptionResult transcribe(Path recording, String filename) {
        try {
            return request(recording, true);
        } catch (UnsupportedResponseFormatException e) {
            // Not every transcription model accepts verbose_json. Falling back
            // keeps the feature working; the result records what was used.
            log.info("Model {} rejected verbose_json; retrying as plain json", properties.openai().transcriptionModel());
            return request(recording, false);
        }
    }

    private TranscriptionResult request(Path recording, boolean richFormat) {
        TranscriptionCreateParams.Builder params = TranscriptionCreateParams.builder()
                .model(properties.openai().transcriptionModel())
                .file(recording)
                .prompt(VERBATIM_PROMPT)
                // The app is English-only; naming the language avoids a
                // misdetection turning a hesitant answer into another language.
                .language("en");

        if (richFormat) {
            params.responseFormat(AudioResponseFormat.VERBOSE_JSON)
                    .addTimestampGranularity(TranscriptionCreateParams.TimestampGranularity.WORD);
        }

        long startedAt = System.nanoTime();
        TranscriptionCreateResponse response;
        try {
            response = client.audio().transcriptions().create(params.build());
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            if (richFormat && mentionsResponseFormat(e)) {
                throw new UnsupportedResponseFormatException();
            }
            throw OpenAiErrors.translate("transcription", e);
        }

        long latencyMillis = (System.nanoTime() - startedAt) / 1_000_000;
        TranscriptionResult result = toResult(response, latencyMillis, richFormat);

        log.info(
                "Transcription ok chars={} words={} timestamps={} avgConfidence={} format={} latencyMs={}",
                result.text().length(),
                result.words().size(),
                result.hasWordTimestamps(),
                result.averageWordConfidence().isPresent()
                        ? "%.3f".formatted(result.averageWordConfidence().getAsDouble())
                        : "n/a",
                result.responseFormat(),
                latencyMillis);

        if (result.text().isBlank()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "No speech was detected in that recording. Check your microphone and try again.");
        }
        return result;
    }

    private TranscriptionResult toResult(
            TranscriptionCreateResponse response, long latencyMillis, boolean richFormat) {

        Optional<TranscriptionVerbose> verbose = response.verbose();
        if (verbose.isPresent()) {
            TranscriptionVerbose body = verbose.get();

            List<TranscriptionResult.Word> words = body.words().orElse(List.of()).stream()
                    .map(word -> new TranscriptionResult.Word(
                            word.word(), word.start(), word.end(), OptionalDouble.empty()))
                    .toList();

            return new TranscriptionResult(
                    body.text().trim(),
                    words,
                    OptionalDouble.empty(),
                    -1,
                    -1,
                    latencyMillis,
                    "verbose_json",
                    true);
        }

        Transcription body = response.transcription()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.PROVIDER_UNAVAILABLE, "The transcription service returned no text."));

        return new TranscriptionResult(
                body.text().trim(),
                List.of(),
                averageConfidence(body),
                inputTokens(body),
                outputTokens(body),
                latencyMillis,
                richFormat ? "verbose_json" : "json",
                true);
    }

    /**
     * Token logprobs are natural-log probabilities; averaging their exponentials
     * gives a mean per-token confidence between 0 and 1.
     */
    private static OptionalDouble averageConfidence(Transcription body) {
        List<Transcription.Logprob> logprobs = body.logprobs().orElse(List.of());
        List<Double> probabilities = new ArrayList<>(logprobs.size());

        for (Transcription.Logprob logprob : logprobs) {
            logprob.logprob().ifPresent(value -> probabilities.add(Math.exp(value)));
        }
        return probabilities.isEmpty()
                ? OptionalDouble.empty()
                : OptionalDouble.of(probabilities.stream().mapToDouble(Double::doubleValue).average().orElse(0));
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

    private static boolean mentionsResponseFormat(RuntimeException e) {
        String message = String.valueOf(e.getMessage()).toLowerCase(java.util.Locale.ROOT);
        return message.contains("response_format")
                || message.contains("verbose_json")
                || message.contains("timestamp_granularities");
    }

    /** Internal signal to retry without the richer response format. */
    private static final class UnsupportedResponseFormatException extends RuntimeException {
        UnsupportedResponseFormatException() {
            super(null, null, false, false);
        }
    }
}
