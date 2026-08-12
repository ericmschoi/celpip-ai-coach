package com.listenspeak.coach.speaking.evaluation;

import com.listenspeak.coach.platform.config.AppProperties.ContentMode;
import com.listenspeak.coach.speaking.SpeakingTaskCatalog.SpeakingTask;
import com.listenspeak.coach.speaking.domain.DeliveryMetrics;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation;
import java.util.List;

/**
 * Scores one transcribed answer.
 *
 * <p>Implementations return a {@link Assessment}; clamping and validation of
 * every number happen in {@link ScoreGuard}, on the server, whatever the source.
 */
public interface SpeakingScorer {

    record Assessment(
            int estimatedLevel,
            SpeakingEvaluation.Confidence confidence,
            List<SpeakingEvaluation.DimensionScore> dimensions,
            List<String> strengths,
            List<SpeakingEvaluation.Improvement> improvements,
            List<SpeakingEvaluation.Correction> corrections,
            String sampleAnswer,
            String nextDrill,
            String sourceRef) {}

    ContentMode mode();

    Assessment score(SpeakingTask task, String promptText, String transcript, DeliveryMetrics metrics);
}
