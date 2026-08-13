package com.listenspeak.coach.speaking.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * The measuring instrument for the live transcription test, so it is checked
 * before it is trusted to judge a provider.
 */
class TranscriptAccuracyTest {

    private static final String REFERENCE =
            "um so I I think she should probably uh take the promotion you know because it is more money";

    @Test
    void aPerfectTranscriptScoresZeroErrors() {
        var report = TranscriptAccuracy.compare(REFERENCE, REFERENCE);

        assertThat(report.wordErrorRate()).isZero();
        assertThat(report.substitutions()).isZero();
        assertThat(report.deletions()).isZero();
        assertThat(report.insertions()).isZero();
    }

    @Test
    void ignoresCasingAndPunctuation() {
        var report = TranscriptAccuracy.compare("I think, she should!", "i think she should");

        assertThat(report.wordErrorRate()).isZero();
    }

    @Test
    void countsASubstitutedWord() {
        var report = TranscriptAccuracy.compare("take the promotion", "take the position");

        assertThat(report.substitutions()).isEqualTo(1);
        assertThat(report.wordErrorRate()).isCloseTo(1.0 / 3, within(0.001));
    }

    @Test
    void countsADroppedWord() {
        var report = TranscriptAccuracy.compare("take the promotion now", "take the promotion");

        assertThat(report.deletions()).isEqualTo(1);
    }

    @Test
    void countsAnAddedWord() {
        var report = TranscriptAccuracy.compare("take the promotion", "take the big promotion");

        assertThat(report.insertions()).isEqualTo(1);
    }

    /** The property the whole live test exists to check. */
    @Test
    void detectsATranscriptThatStrippedEveryFiller() {
        String cleaned = "so I think she should take the promotion because it is more money";

        var report = TranscriptAccuracy.compare(REFERENCE, cleaned);

        assertThat(report.referenceFillers()).isGreaterThan(0);
        assertThat(report.fillerRecall()).isLessThan(0.6);
        assertThat(report.missingWords()).anySatisfy(word -> assertThat(word).startsWith("um"));
    }

    @Test
    void detectsATranscriptThatCollapsedARepeatedWord() {
        String collapsed = "um so I think she should probably uh take the promotion you know because it is more money";

        var report = TranscriptAccuracy.compare(REFERENCE, collapsed);

        assertThat(report.referenceRepetitions()).isEqualTo(1);
        assertThat(report.recognisedRepetitions()).isZero();
        assertThat(report.repetitionRecall()).isZero();
    }

    @Test
    void keepsFillersWhenTheyAreActuallyPreserved() {
        var report = TranscriptAccuracy.compare(REFERENCE, REFERENCE);

        assertThat(report.fillerRecall()).isEqualTo(1);
        assertThat(report.repetitionRecall()).isEqualTo(1);
    }

    @Test
    void listsTheWordsThatWentMissing() {
        var report = TranscriptAccuracy.compare("take the promotion now please", "take the promotion");

        assertThat(report.missingWords()).contains("now x1", "please x1");
    }

    @Test
    void listsWordsTheModelAddedThatWereNeverSaid() {
        var report = TranscriptAccuracy.compare("take the promotion", "take the promotion tomorrow");

        assertThat(report.unexpectedWords()).contains("tomorrow x1");
    }

    @Test
    void summaryReportsEveryFigureTheLiveTestMustState() {
        String summary = TranscriptAccuracy.compare(REFERENCE, "so I think she should take it").summary();

        assertThat(summary)
                .contains("word error rate")
                .contains("filler recall")
                .contains("repetition recall")
                .contains("substitutions");
    }
}
