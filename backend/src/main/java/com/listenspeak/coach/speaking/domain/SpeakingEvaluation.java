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
 * @param estimatedLevel unofficial 1-12 estimate. Never described as an official score.
 * @param confidence how much weight the estimate deserves given the evidence available
 */
public record SpeakingEvaluation(
        UUID id,
        String ownerId,
        UUID promptId,
        int taskNumber,
        String transcript,
        DeliveryMetrics metrics,
        int estimatedLevel,
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
     * @param evidence must quote the user's own transcript or a delivery metric
     */
    public record DimensionScore(Dimension dimension, int score, String evidence) {}

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

    public static int clampLevel(int value) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, value));
    }

    public boolean isOwnedBy(String userId) {
        return ownerId.equals(userId);
    }
}
