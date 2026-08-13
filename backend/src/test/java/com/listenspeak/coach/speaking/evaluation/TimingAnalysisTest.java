package com.listenspeak.coach.speaking.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.listenspeak.coach.speaking.domain.DeliveryMetrics;
import com.listenspeak.coach.speaking.domain.TimingMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Timing is an enhancement. Losing it must cost the user nothing except the
 * timestamp-derived numbers, and those must then read as absent rather than as
 * a measurement of zero.
 */
class TimingAnalysisTest {

    private static TimingAnalysis withWords(TimingAnalysis.TimedWord... words) {
        return new TimingAnalysis(true, null, "whisper-1", "verbose_json", 900, List.of(words), List.of());
    }

    @Test
    void computesSpeakingTimeFromWordSpansRatherThanWallClock() {
        TimingMetrics timing = TimingMetrics.from(withWords(
                new TimingAnalysis.TimedWord("so", 0.0, 0.4),
                new TimingAnalysis.TimedWord("um", 2.0, 2.6),
                new TimingAnalysis.TimedWord("yes", 3.0, 3.4)));

        // 0.4 + 0.6 + 0.4 of actual word production inside 3.4 seconds elapsed.
        assertThat(timing.speakingSeconds()).isEqualTo(1.4);
    }

    @Test
    void countsPausesAndFindsTheLongest() {
        TimingMetrics timing = TimingMetrics.from(withWords(
                new TimingAnalysis.TimedWord("one", 0.0, 0.3),
                new TimingAnalysis.TimedWord("two", 1.5, 1.8),
                new TimingAnalysis.TimedWord("three", 1.9, 2.2),
                new TimingAnalysis.TimedWord("four", 5.0, 5.3)));

        // Gaps: 1.2s (pause), 0.1s (not), 2.8s (pause).
        assertThat(timing.pauseCount()).isEqualTo(2);
        assertThat(timing.longestPauseSeconds()).isEqualTo(2.8);
    }

    @Test
    void locatesFillersInTime() {
        TimingMetrics timing = TimingMetrics.from(withWords(
                new TimingAnalysis.TimedWord("so", 0.0, 0.3),
                new TimingAnalysis.TimedWord("um,", 0.4, 0.9),
                new TimingAnalysis.TimedWord("yes", 1.0, 1.3)));

        assertThat(timing.fillerOccurrences()).hasSize(1);
        assertThat(timing.fillerOccurrences().get(0).word()).isEqualTo("um");
        assertThat(timing.fillerOccurrences().get(0).atSeconds()).isEqualTo(0.4);
    }

    // --- degradation --------------------------------------------------------

    @Test
    void producesNoTimingMetricsWhenTheTimingPassWasUnavailable() {
        TimingAnalysis unavailable = TimingAnalysis.unavailable("whisper-1", "The timing request failed.");

        assertThat(TimingMetrics.from(unavailable)).isNull();
    }

    @Test
    void producesNoTimingMetricsWhenTheModelReturnedNoWords() {
        TimingAnalysis empty = new TimingAnalysis(true, null, "whisper-1", "verbose_json", 800, List.of(), List.of());

        assertThat(TimingMetrics.from(empty)).isNull();
    }

    @Test
    void missingTimingLeavesTheMetricAbsentRatherThanZero() {
        DeliveryMetrics metrics = DeliveryMetrics.of("um so I think yes", 30, 60, 0.2, 1.5, null);

        assertThat(metrics.timing()).isNull();
        // FFmpeg-derived measurements survive and stay real.
        assertThat(metrics.durationSeconds()).isEqualTo(30);
        assertThat(metrics.silenceRatio()).isEqualTo(0.2);
        assertThat(metrics.longestSilenceSeconds()).isEqualTo(1.5);
    }

    @Test
    void theScoringPromptSaysTimingIsUnavailableRatherThanReportingZeroes() {
        String summary = DeliveryMetrics.of("um so I think yes", 30, 60, 0.2, 1.5, null).summary();

        assertThat(summary).contains("word timestamps: unavailable");
        assertThat(summary).doesNotContain("pace from timestamps");
    }

    @Test
    void theScoringPromptIncludesTimingWhenItIsAvailable() {
        TimingMetrics timing = TimingMetrics.from(withWords(
                new TimingAnalysis.TimedWord("one", 0.0, 0.4),
                new TimingAnalysis.TimedWord("two", 1.5, 1.9)));

        String summary = DeliveryMetrics.of("one two", 30, 60, 0.2, 1.5, timing).summary();

        assertThat(summary).contains("pace from timestamps");
        assertThat(summary).doesNotContain("unavailable");
    }

    @Test
    void unavailableTimingCarriesItsReasonInsteadOfFailingSilently() {
        TimingAnalysis unavailable =
                TimingAnalysis.unavailable("gpt-transcribe", "Model gpt-transcribe does not support timestamp_granularities.");

        assertThat(unavailable.available()).isFalse();
        assertThat(unavailable.hasWordTimestamps()).isFalse();
        assertThat(unavailable.unavailableReason()).contains("does not support timestamp_granularities");
    }
}
