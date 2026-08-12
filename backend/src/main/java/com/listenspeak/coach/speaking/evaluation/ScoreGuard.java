package com.listenspeak.coach.speaking.evaluation;

import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.Confidence;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.Dimension;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.DimensionScore;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Server-side validation of an assessment before the user ever sees it.
 *
 * <p>A schema constrains what a model may emit; it cannot stop a missing
 * dimension, an out-of-range number that slipped through, or an overall level
 * that contradicts its own dimension scores. All of that is fixed or rejected
 * here.
 */
@Component
public class ScoreGuard {

    private static final Logger log = LoggerFactory.getLogger(ScoreGuard.class);

    /** How far the overall level may sit from the mean of the dimensions. */
    private static final int MAX_DIVERGENCE = 2;

    private static final int MAX_LIST_ITEMS = 6;

    public SpeakingScorer.Assessment validate(SpeakingScorer.Assessment assessment) {
        List<DimensionScore> dimensions = normalizeDimensions(assessment.dimensions());

        int level = SpeakingEvaluation.clampLevel(assessment.estimatedLevel());
        int mean = (int) Math.round(
                dimensions.stream().mapToInt(DimensionScore::score).average().orElse(level));

        if (Math.abs(level - mean) > MAX_DIVERGENCE) {
            // The overall level must be explainable by its own dimension scores.
            log.warn("Clamping estimated level {} towards dimension mean {}", level, mean);
            level = mean + Integer.signum(level - mean) * MAX_DIVERGENCE;
            level = SpeakingEvaluation.clampLevel(level);
        }

        if (isBlank(assessment.sampleAnswer())) {
            throw new ApiException(ErrorCode.GENERATION_INVALID, "The evaluation came back without a sample answer.");
        }
        if (isBlank(assessment.nextDrill())) {
            throw new ApiException(ErrorCode.GENERATION_INVALID, "The evaluation came back without a next step.");
        }

        return new SpeakingScorer.Assessment(
                level,
                assessment.confidence() == null ? Confidence.LOW : assessment.confidence(),
                dimensions,
                trim(assessment.strengths()),
                trim(assessment.improvements()),
                trim(assessment.corrections()),
                assessment.sampleAnswer().trim(),
                assessment.nextDrill().trim(),
                assessment.sourceRef());
    }

    /**
     * All four dimensions must be present exactly once, each with a clamped
     * score and non-empty evidence.
     */
    private List<DimensionScore> normalizeDimensions(List<DimensionScore> reported) {
        Map<Dimension, DimensionScore> byDimension = new EnumMap<>(Dimension.class);

        for (DimensionScore score : reported) {
            if (score.dimension() == null) {
                continue;
            }
            byDimension.putIfAbsent(
                    score.dimension(),
                    new DimensionScore(
                            score.dimension(),
                            SpeakingEvaluation.clampLevel(score.score()),
                            isBlank(score.evidence()) ? "No specific evidence was provided." : score.evidence().trim()));
        }

        List<DimensionScore> complete = new ArrayList<>(Dimension.values().length);
        for (Dimension dimension : Dimension.values()) {
            DimensionScore score = byDimension.get(dimension);
            if (score == null) {
                throw new ApiException(
                        ErrorCode.GENERATION_INVALID,
                        "The evaluation was missing the %s dimension.".formatted(dimension.label()));
            }
            complete.add(score);
        }
        return List.copyOf(complete);
    }

    private static <T> List<T> trim(List<T> items) {
        if (items == null) {
            return List.of();
        }
        return List.copyOf(items.subList(0, Math.min(items.size(), MAX_LIST_ITEMS)));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
