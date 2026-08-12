package com.listenspeak.coach.speaking.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.Confidence;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.Correction;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.Dimension;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.DimensionScore;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.Improvement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A model can emit anything the schema permits, and schemas do not enforce
 * coherence. This is the last check before a number reaches the user.
 */
class ScoreGuardTest {

    private final ScoreGuard guard = new ScoreGuard();

    private List<DimensionScore> dimensions(int content, int vocabulary, int listenability, int fulfillment) {
        return List.of(
                new DimensionScore(Dimension.CONTENT_COHERENCE, content, "because they said X"),
                new DimensionScore(Dimension.VOCABULARY, vocabulary, "because they said Y"),
                new DimensionScore(Dimension.LISTENABILITY, listenability, "pace was 140 wpm"),
                new DimensionScore(Dimension.TASK_FULFILLMENT, fulfillment, "used 85% of the time"));
    }

    private SpeakingScorer.Assessment assessment(int level, List<DimensionScore> dimensions) {
        return new SpeakingScorer.Assessment(
                level,
                Confidence.MEDIUM,
                dimensions,
                List.of("clear opening"),
                List.of(new Improvement("thin support", "reasons matter", "add a because clause")),
                List.of(new Correction("I go yesterday", "I went yesterday", "past tense")),
                "A stronger version of the same answer.",
                "Record it again and time yourself.",
                "test");
    }

    @Test
    void passesACoherentAssessmentThrough() {
        var result = guard.validate(assessment(8, dimensions(8, 8, 7, 9)));

        assertThat(result.estimatedLevel()).isEqualTo(8);
        assertThat(result.dimensions()).hasSize(4);
    }

    @Test
    void clampsALevelAboveTwelve() {
        assertThat(guard.validate(assessment(47, dimensions(12, 12, 12, 12))).estimatedLevel())
                .isEqualTo(12);
    }

    @Test
    void clampsALevelBelowOne() {
        assertThat(guard.validate(assessment(-3, dimensions(1, 1, 1, 1))).estimatedLevel())
                .isEqualTo(1);
    }

    @Test
    void clampsAnOutOfRangeDimensionScore() {
        List<DimensionScore> wild = List.of(
                new DimensionScore(Dimension.CONTENT_COHERENCE, 99, "evidence"),
                new DimensionScore(Dimension.VOCABULARY, -5, "evidence"),
                new DimensionScore(Dimension.LISTENABILITY, 7, "evidence"),
                new DimensionScore(Dimension.TASK_FULFILLMENT, 7, "evidence"));

        assertThat(guard.validate(assessment(8, wild)).dimensions())
                .extracting(DimensionScore::score)
                .allSatisfy(score -> assertThat(score).isBetween(1, 12));
    }

    @Test
    void pullsAnOverallLevelBackTowardsItsOwnDimensionScores() {
        // Dimensions average 4, so an overall 12 is not explainable by them.
        var result = guard.validate(assessment(12, dimensions(4, 4, 4, 4)));

        assertThat(result.estimatedLevel()).isEqualTo(6);
    }

    @Test
    void rejectsAnAssessmentMissingADimension() {
        List<DimensionScore> incomplete = new ArrayList<>(dimensions(8, 8, 8, 8));
        incomplete.removeIf(score -> score.dimension() == Dimension.LISTENABILITY);

        assertThatThrownBy(() -> guard.validate(assessment(8, incomplete)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Listenability");
    }

    @Test
    void keepsTheFirstScoreWhenADimensionIsReportedTwice() {
        List<DimensionScore> duplicated = new ArrayList<>(dimensions(8, 8, 8, 8));
        duplicated.add(new DimensionScore(Dimension.VOCABULARY, 2, "contradictory second opinion"));

        var result = guard.validate(assessment(8, duplicated));

        assertThat(result.dimensions()).hasSize(4);
        assertThat(result.dimensions()).filteredOn(score -> score.dimension() == Dimension.VOCABULARY)
                .extracting(DimensionScore::score)
                .containsExactly(8);
    }

    @Test
    void substitutesEvidenceRatherThanShowingAnEmptyField() {
        List<DimensionScore> blank = List.of(
                new DimensionScore(Dimension.CONTENT_COHERENCE, 7, "  "),
                new DimensionScore(Dimension.VOCABULARY, 7, "evidence"),
                new DimensionScore(Dimension.LISTENABILITY, 7, "evidence"),
                new DimensionScore(Dimension.TASK_FULFILLMENT, 7, "evidence"));

        assertThat(guard.validate(assessment(7, blank)).dimensions())
                .extracting(DimensionScore::evidence)
                .allSatisfy(evidence -> assertThat(evidence).isNotBlank());
    }

    @Test
    void rejectsAnAssessmentWithoutASampleAnswer() {
        var missing = new SpeakingScorer.Assessment(
                8, Confidence.MEDIUM, dimensions(8, 8, 8, 8), List.of(), List.of(), List.of(), " ", "drill", "test");

        assertThatThrownBy(() -> guard.validate(missing))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("sample answer");
    }

    @Test
    void rejectsAnAssessmentWithoutANextStep() {
        var missing = new SpeakingScorer.Assessment(
                8, Confidence.MEDIUM, dimensions(8, 8, 8, 8), List.of(), List.of(), List.of(), "sample", null, "test");

        assertThatThrownBy(() -> guard.validate(missing))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("next step");
    }

    @Test
    void defaultsToLowConfidenceWhenNoneWasReported() {
        var noConfidence = new SpeakingScorer.Assessment(
                8, null, dimensions(8, 8, 8, 8), List.of(), List.of(), List.of(), "sample", "drill", "test");

        assertThat(guard.validate(noConfidence).confidence()).isEqualTo(Confidence.LOW);
    }
}
