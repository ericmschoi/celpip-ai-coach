package com.listenspeak.coach.speaking.evaluation;

/**
 * The user-facing transcript and what is genuinely known about the call that
 * produced it.
 *
 * <p>There is deliberately no confidence field. The primary model,
 * {@code gpt-transcribe}, does not return transcription logprobs, so any
 * confidence number here would be invented. Timing and diagnostics live in
 * {@link TimingAnalysis}, which comes from a different model.
 *
 * @param inputTokens provider-reported, or -1 when not reported
 * @param outputTokens provider-reported, or -1 when not reported
 * @param responseFormat the format actually requested, reported rather than assumed
 */
public record TranscriptionResult(
        String text,
        String model,
        String responseFormat,
        long inputTokens,
        long outputTokens,
        long latencyMillis,
        boolean verbatimRequested) {

    /** Used only where transcription is genuinely unavailable. */
    public static TranscriptionResult unavailable() {
        return new TranscriptionResult("", "none", "none", -1, -1, 0, false);
    }

    public boolean isAvailable() {
        return !text.isBlank();
    }
}
