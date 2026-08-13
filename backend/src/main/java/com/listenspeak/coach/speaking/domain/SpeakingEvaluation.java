package com.listenspeak.coach.speaking.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The result of assessing one recorded answer.
 *
 * <p>Every numeric score is clamped on the server before it gets here, so a
 * model cannot produce a level of 47 or a negative dimension score.
 *
 * <p>Scores are nullable on purpose. When something could not be assessed - most
 * obviously when no transcription is available and so nothing is known about
 * what was actually said - the honest representation is the absence of a score,
 * not a number derived from something else.
 *
 * @param transcript exactly what was said, or empty when transcription was unavailable
 * @param transcriptAvailable false when nothing could be transcribed, so no part
 *     of this evaluation may be presented as the user's own words
 * @param estimatedLevel unofficial 1-12 estimate, or null when it cannot be
 *     honestly estimated. Never described as an official score.
 * @param confidence how much weight the estimate deserves given the evidence available
 */
public record SpeakingEvaluation(
        UUID id,
        String ownerId,
        UUID promptId,
        int taskNumber,
        String transcript,
        boolean transcriptAvailable,
        TranscriptionQuality transcriptionQuality,
        DeliveryMetrics metrics,
        Integer estimatedLevel,
        Confidence confidence,
        List<DimensionScore> dimensions,
        List<String> strengths,
        List<Improvement> improvements,
        List<Correction> corrections,
        String sampleAnswer,
        String nextDrill,
        String sourceRef,
        Instant createdAt) {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 12;

    /**
     * How the transcript and the timings were obtained.
     *
     * <p>There is no confidence field. The primary transcription model does not
     * return logprobs, and whisper's {@code avg_logprob} is uncalibrated, so any
     * number here would be invented. Missing timing is reported with its reason
     * rather than as zero.
     */
    public record TranscriptionQuality(
            String transcriptModel,
            String transcriptResponseFormat,
            boolean verbatimRequested,
            long transcriptLatencyMillis,
            boolean wordTimestampsAvailable,
            int timedWordCount,
            String timingModel,
            String timingResponseFormat,
            long timingLatencyMillis,
            String timingUnavailableReason) {

        public static TranscriptionQuality unavailable() {
            return new TranscriptionQuality(
                    "none", "none", false, 0, false, 0, "none", "none", 0, "No transcription was performed.");
        }
    }

    public enum Confidence {
        LOW,
        MEDIUM,
        HIGH
    }

    /** The four dimensions the evaluation always reports. */
    public enum Dimension {
        CONTENT_COHERENCE("Content and Coherence"),
        VOCABULARY("Vocabulary"),
        LISTENABILITY("Listenability"),
        TASK_FULFILLMENT("Task Fulfillment");

        private final String label;

        Dimension(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * @param score 1-12, or null when this dimension could not be assessed
     * @param evidence must quote the user's own transcript or a delivery metric,
     *     or state plainly why the dimension was not assessed
     */
    public record DimensionScore(Dimension dimension, Integer score, String evidence) {

        public boolean assessed() {
            return score != null;
        }
    }

    public record Improvement(String issue, String whyItMatters, String howToFix) {}

    /**
     * @param original what the speaker actually said, quoted from the transcript
     * @param improved a stronger phrasing that keeps their meaning
     */
    public record Correction(String original, String improved, String reason) {}

    public SpeakingEvaluation {
        dimensions = List.copyOf(dimensions);
        strengths = List.copyOf(strengths);
        improvements = List.copyOf(improvements);
        corrections = List.copyOf(corrections);
    }

    public static Integer clampLevel(Integer value) {
        return value == null ? null : Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, value));
    }

    public boolean isOwnedBy(String userId) {
        return ownerId.equals(userId);
    }
}
