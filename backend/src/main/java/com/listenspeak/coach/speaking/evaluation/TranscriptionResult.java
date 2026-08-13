package com.listenspeak.coach.speaking.evaluation;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Everything the transcription step learned, not just the text.
 *
 * <p>The earlier version kept only the text and discarded timestamps,
 * confidence, and usage. That made it impossible to answer the questions that
 * decide whether transcription is good enough to score against: did it keep the
 * fillers, how sure was it, and how long did it take.
 *
 * @param text exactly what was said, verbatim where the model supports it
 * @param words per-word timings, empty when the model did not return them
 * @param averageWordConfidence mean per-token probability, empty when no logprobs were returned
 * @param inputTokens provider-reported input tokens, or -1 when not reported
 * @param outputTokens provider-reported output tokens, or -1 when not reported
 * @param latencyMillis wall-clock time for the provider call
 * @param responseFormat the format actually accepted by the model, for the live report
 * @param verbatimRequested whether disfluency-preserving instructions were sent
 */
public record TranscriptionResult(
        String text,
        List<Word> words,
        OptionalDouble averageWordConfidence,
        long inputTokens,
        long outputTokens,
        long latencyMillis,
        String responseFormat,
        boolean verbatimRequested) {

    /** @param confidence per-word probability, or empty when the model did not report one */
    public record Word(String text, double startSeconds, double endSeconds, OptionalDouble confidence) {}

    public TranscriptionResult {
        words = List.copyOf(words);
    }

    public boolean hasWordTimestamps() {
        return !words.isEmpty();
    }

    public static TranscriptionResult textOnly(String text, long latencyMillis, String responseFormat) {
        return new TranscriptionResult(
                text, List.of(), OptionalDouble.empty(), -1, -1, latencyMillis, responseFormat, true);
    }

    /** Empty result, used only where transcription is genuinely unavailable. */
    public static TranscriptionResult unavailable() {
        return new TranscriptionResult("", List.of(), OptionalDouble.empty(), -1, -1, 0, "none", false);
    }
}
