package com.listenspeak.coach.speaking.api;

import com.listenspeak.coach.speaking.SpeakingTaskCatalog;
import com.listenspeak.coach.speaking.domain.DeliveryMetrics;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response types for the speaking API. */
public final class SpeakingViews {

    public static final String AI_ESTIMATE_NOTICE =
            "This is an AI estimate for practice only, not an official CELPIP score.";

    private SpeakingViews() {}

    public record TaskView(int taskNumber, String title, String focus, int preparationSeconds, int answerSeconds) {

        public static TaskView of(SpeakingTaskCatalog.SpeakingTask task) {
            return new TaskView(
                    task.taskNumber(),
                    task.title(),
                    task.focus(),
                    (int) task.preparation().toSeconds(),
                    (int) task.answer().toSeconds());
        }
    }

    public record PromptView(
            UUID id,
            int taskNumber,
            String taskTitle,
            String situation,
            String instruction,
            List<String> bullets,
            int preparationSeconds,
            int answerSeconds,
            Instant createdAt) {

        public static PromptView of(com.listenspeak.coach.speaking.domain.SpeakingPrompt prompt) {
            return new PromptView(
                    prompt.id(),
                    prompt.taskNumber(),
                    prompt.taskTitle(),
                    prompt.situation(),
                    prompt.instruction(),
                    prompt.bullets(),
                    prompt.preparationSeconds(),
                    prompt.answerSeconds(),
                    prompt.createdAt());
        }
    }

    public record MetricsView(
            double durationSeconds,
            int allowedSeconds,
            int timeUsedPercent,
            int wordCount,
            int wordsPerMinute,
            int fillerCount,
            int repeatedStarts,
            int silencePercent,
            double longestSilenceSeconds) {

        public static MetricsView of(DeliveryMetrics metrics) {
            return new MetricsView(
                    metrics.durationSeconds(),
                    metrics.allowedSeconds(),
                    metrics.timeUsedPercent(),
                    metrics.wordCount(),
                    (int) Math.round(metrics.wordsPerMinute()),
                    metrics.fillerCount(),
                    metrics.repeatedStarts(),
                    (int) Math.round(metrics.silenceRatio() * 100),
                    metrics.longestSilenceSeconds());
        }
    }

    /** {@code score} is null when the dimension could not be assessed. */
    public record DimensionView(
            String dimension, String label, Integer score, boolean assessed, String evidence) {}

    public record ImprovementView(String issue, String whyItMatters, String howToFix) {}

    public record CorrectionView(String original, String improved, String reason) {}

    /**
     * The full evaluation. {@code disclaimer} is part of the payload rather than
     * only the UI, so any client of this API carries the same caveat.
     */
    public record EvaluationView(
            UUID id,
            UUID promptId,
            int taskNumber,
            /** Null when no honest estimate is possible; the UI must not invent one. */
            Integer estimatedLevel,
            String confidence,
            String disclaimer,
            /** False when nothing could be transcribed, so no text here is the user's. */
            boolean transcriptAvailable,
            List<DimensionView> dimensions,
            List<String> strengths,
            List<ImprovementView> improvements,
            List<CorrectionView> corrections,
            String sampleAnswer,
            String nextDrill,
            String transcript,
            MetricsView metrics,
            Instant createdAt) {

        public static EvaluationView of(SpeakingEvaluation evaluation) {
            return new EvaluationView(
                    evaluation.id(),
                    evaluation.promptId(),
                    evaluation.taskNumber(),
                    evaluation.estimatedLevel(),
                    evaluation.confidence().name(),
                    AI_ESTIMATE_NOTICE,
                    evaluation.transcriptAvailable(),
                    evaluation.dimensions().stream()
                            .map(dimension -> new DimensionView(
                                    dimension.dimension().name(),
                                    dimension.dimension().label(),
                                    dimension.score(),
                                    dimension.assessed(),
                                    dimension.evidence()))
                            .toList(),
                    evaluation.strengths(),
                    evaluation.improvements().stream()
                            .map(improvement -> new ImprovementView(
                                    improvement.issue(), improvement.whyItMatters(), improvement.howToFix()))
                            .toList(),
                    evaluation.corrections().stream()
                            .map(correction -> new CorrectionView(
                                    correction.original(), correction.improved(), correction.reason()))
                            .toList(),
                    evaluation.sampleAnswer(),
                    evaluation.nextDrill(),
                    evaluation.transcript(),
                    MetricsView.of(evaluation.metrics()),
                    evaluation.createdAt());
        }
    }
}
