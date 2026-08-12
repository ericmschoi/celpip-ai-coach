package com.listenspeak.coach.speaking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeliveryMetricsTest {

    @Test
    void countsWordsIgnoringPunctuation() {
        DeliveryMetrics metrics = DeliveryMetrics.of("Well, I'd say yes - definitely!", 10, 60, 0, 0);

        assertThat(metrics.wordCount()).isEqualTo(5);
    }

    @Test
    void computesPaceOverSpeakingTimeRatherThanTotalTime() {
        // 100 words, 60 seconds recorded, half of it silent -> 200 wpm while speaking.
        String transcript = "word ".repeat(100);

        DeliveryMetrics metrics = DeliveryMetrics.of(transcript, 60, 90, 0.5, 3);

        assertThat(metrics.wordsPerMinute()).isEqualTo(200.0);
    }

    @Test
    void countsSingleWordAndMultiWordFillers() {
        DeliveryMetrics metrics = DeliveryMetrics.of(
                "So um I think, you know, it is uh like the best option, I mean, really.", 20, 60, 0, 0);

        // um, you know, uh, like, i mean
        assertThat(metrics.fillerCount()).isEqualTo(5);
    }

    @Test
    void countsRepeatedWordStarts() {
        DeliveryMetrics metrics =
                DeliveryMetrics.of("the the answer is is clear about that", 10, 60, 0, 0);

        assertThat(metrics.repeatedStarts()).isEqualTo(2);
    }

    @Test
    void doesNotCountASingleLetterRepeatAsHesitation() {
        DeliveryMetrics metrics = DeliveryMetrics.of("a a bit later", 10, 60, 0, 0);

        assertThat(metrics.repeatedStarts()).isZero();
    }

    @Test
    void reportsHowMuchOfTheAllowedTimeWasUsed() {
        DeliveryMetrics metrics = DeliveryMetrics.of("some words here", 45, 90, 0, 0);

        assertThat(metrics.timeUsedPercent()).isEqualTo(50);
        assertThat(metrics.usedTimePoorly()).isTrue();
    }

    @Test
    void doesNotFlagAnAnswerThatUsedMostOfTheTime() {
        DeliveryMetrics metrics = DeliveryMetrics.of("some words here", 80, 90, 0, 0);

        assertThat(metrics.usedTimePoorly()).isFalse();
    }

    @Test
    void survivesAnEmptyTranscriptWithoutDividingByZero() {
        DeliveryMetrics metrics = DeliveryMetrics.of("", 30, 60, 1.0, 30);

        assertThat(metrics.wordCount()).isZero();
        assertThat(metrics.wordsPerMinute()).isZero();
    }

    @Test
    void summaryCarriesEveryMeasurementTheScoringPromptNeeds() {
        String summary = DeliveryMetrics.of("one two three", 30, 60, 0.2, 2.5).summary();

        assertThat(summary)
                .contains("duration")
                .contains("words")
                .contains("pace")
                .contains("fillers")
                .contains("silence");
    }
}
