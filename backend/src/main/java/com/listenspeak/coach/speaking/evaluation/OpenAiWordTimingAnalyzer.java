package com.listenspeak.coach.speaking.evaluation;

import com.listenspeak.coach.platform.config.AppProperties;
import com.listenspeak.coach.platform.openai.OpenAiConfiguredCondition;
import com.listenspeak.coach.speaking.evaluation.TranscriptionModels.Capability;
import com.openai.models.audio.AudioResponseFormat;
import com.openai.client.OpenAIClient;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import com.openai.models.audio.transcriptions.TranscriptionVerbose;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Word and segment timings from {@code whisper-1}, the only model that supports
 * {@code timestamp_granularities} and {@code verbose_json}.
 *
 * <p>This never throws. If the request fails, or the configured timing model
 * does not support timestamps, it returns an unavailable result with the reason
 * and the caller falls back to FFmpeg-derived duration and silence. Nothing is
 * invented to fill the gap.
 */
@Component
@Conditional(OpenAiConfiguredCondition.class)
public class OpenAiWordTimingAnalyzer implements WordTimingAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(OpenAiWordTimingAnalyzer.class);

    /** Short, because whisper-1 accepts a far smaller prompt than the primary model. */
    static final String VERBATIM_PROMPT =
            "Verbatim transcript. Keep fillers um, uh, er, like, you know. "
                    + "Keep repeated words, false starts, and self-corrections.";

    private final OpenAIClient client;
    private final AppProperties properties;

    public OpenAiWordTimingAnalyzer(OpenAIClient client, AppProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public TimingAnalysis analyze(Path recording, String filename) {
        String model = properties.openai().timingModel();

        if (model == null || model.isBlank()) {
            return TimingAnalysis.unavailable("none", "Timing analysis is disabled by configuration.");
        }
        if (!TranscriptionModels.supports(model, Capability.TIMESTAMP_GRANULARITIES)) {
            // Checked rather than attempted: sending it anyway would make a
            // rejected request part of the normal path.
            return TimingAnalysis.unavailable(
                    model, "Model %s does not support timestamp_granularities.".formatted(model));
        }

        TranscriptionCreateParams params = TranscriptionCreateParams.builder()
                .model(model)
                .file(recording)
                .prompt(VERBATIM_PROMPT)
                .language("en")
                .responseFormat(AudioResponseFormat.VERBOSE_JSON)
                .addTimestampGranularity(TranscriptionCreateParams.TimestampGranularity.WORD)
                .addTimestampGranularity(TranscriptionCreateParams.TimestampGranularity.SEGMENT)
                .build();

        long startedAt = System.nanoTime();
        TranscriptionCreateResponse response;
        try {
            response = client.audio().transcriptions().create(params);
        } catch (RuntimeException e) {
            // Timing is an enhancement; losing it must not cost the evaluation.
            log.warn("Timing analysis with {} failed; continuing without word timestamps", model, e);
            return TimingAnalysis.unavailable(model, "The timing request failed.");
        }
        long latencyMillis = (System.nanoTime() - startedAt) / 1_000_000;

        TranscriptionVerbose body = response.verbose().orElse(null);
        if (body == null) {
            log.warn("Timing analysis with {} returned no verbose payload", model);
            return TimingAnalysis.unavailable(model, "The timing response contained no timings.");
        }

        List<TimingAnalysis.TimedWord> words = body.words().orElse(List.of()).stream()
                .map(word -> new TimingAnalysis.TimedWord(word.word(), word.start(), word.end()))
                .toList();

        List<TimingAnalysis.TimedSegment> segments = body.segments().orElse(List.of()).stream()
                .map(segment -> new TimingAnalysis.TimedSegment(
                        segment.text(),
                        segment.start(),
                        segment.end(),
                        segment.avgLogprob(),
                        segment.noSpeechProb()))
                .toList();

        if (words.isEmpty()) {
            return TimingAnalysis.unavailable(model, "The timing response contained no word timings.");
        }

        // Diagnostics only: avg_logprob is uncalibrated and never surfaces to the user.
        log.info(
                "Timing analysis ok model={} words={} segments={} latencyMs={}",
                model,
                words.size(),
                segments.size(),
                latencyMillis);

        return new TimingAnalysis(true, null, model, "verbose_json", latencyMillis, words, segments);
    }
}
