package com.listenspeak.coach.speaking.evaluation;

import java.util.List;

/**
 * Word and segment timings from a separate timing-analysis request.
 *
 * <p>Only {@code whisper-1} returns these, so they come from a second call
 * rather than from the model that produces the user-facing transcript.
 *
 * <p>{@code avgLogprob} and {@code noSpeechProb} are kept as <strong>internal
 * diagnostics only</strong>. They are not calibrated against labelled human
 * transcripts, so they are never converted into a percentage, never shown to the
 * user, and never described as pronunciation confidence or speaking quality.
 *
 * @param unavailableReason why timing is missing, or null when it is present
 */
public record TimingAnalysis(
        boolean available,
        String unavailableReason,
        String model,
        String responseFormat,
        long latencyMillis,
        List<TimedWord> words,
        List<TimedSegment> segments) {

    public record TimedWord(String word, double startSeconds, double endSeconds) {

        public double durationSeconds() {
            return Math.max(0, endSeconds - startSeconds);
        }
    }

    /**
     * @param avgLogprob whisper's mean token logprob for the segment; diagnostic only
     * @param noSpeechProb whisper's probability the segment contains no speech; diagnostic only
     */
    public record TimedSegment(
            String text, double startSeconds, double endSeconds, double avgLogprob, double noSpeechProb) {}

    public TimingAnalysis {
        words = List.copyOf(words);
        segments = List.copyOf(segments);
    }

    public boolean hasWordTimestamps() {
        return available && !words.isEmpty();
    }

    public static TimingAnalysis unavailable(String model, String reason) {
        return new TimingAnalysis(false, reason, model, "none", 0, List.of(), List.of());
    }
}
