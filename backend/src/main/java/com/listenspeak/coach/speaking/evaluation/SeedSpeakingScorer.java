package com.listenspeak.coach.speaking.evaluation;

import com.listenspeak.coach.platform.config.AppProperties.ContentMode;
import com.listenspeak.coach.speaking.SpeakingTaskCatalog.SpeakingTask;
import com.listenspeak.coach.speaking.domain.DeliveryMetrics;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.Confidence;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.Correction;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.Dimension;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.DimensionScore;
import com.listenspeak.coach.speaking.domain.SpeakingEvaluation.Improvement;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Demo-mode scorer, used when no AI provider is configured.
 *
 * <p>It reports only what was genuinely measured from the user's own recording:
 * how long they spoke, how much of the time they used, and how much of it was
 * silence. Everything that would require understanding what they said is
 * reported as <em>not assessed</em> rather than guessed.
 *
 * <p>In particular it produces no corrections and no overall level when there
 * is no transcript. Quoting a phrase the user never said, or attaching a CELPIP
 * band to an answer nothing has listened to, would be fabrication.
 */
@Component
public class SeedSpeakingScorer implements SpeakingScorer {

    private static final String NOT_ASSESSED =
            "Not assessed: demo mode has no AI provider configured, so nothing listened to this answer.";

    @Override
    public ContentMode mode() {
        return ContentMode.SEED;
    }

    @Override
    public Assessment score(
            SpeakingTask task,
            String promptText,
            String transcript,
            boolean transcriptAvailable,
            DeliveryMetrics metrics) {

        if (!transcriptAvailable) {
            return withoutTranscript(metrics);
        }
        return withTranscript(metrics);
    }

    /**
     * No transcription available. Only pacing and use of time were measured, so
     * only those are scored.
     */
    private Assessment withoutTranscript(DeliveryMetrics metrics) {
        int fulfillment = scoreTimeUsed(metrics);
        int listenability = scorePausing(metrics);

        return new Assessment(
                // No overall level: two of four dimensions are unknown, and both
                // of the unknown ones are about language rather than delivery.
                null,
                Confidence.LOW,
                List.of(
                        new DimensionScore(Dimension.CONTENT_COHERENCE, null, NOT_ASSESSED),
                        new DimensionScore(Dimension.VOCABULARY, null, NOT_ASSESSED),
                        new DimensionScore(
                                Dimension.LISTENABILITY,
                                listenability,
                                "Measured from your recording: %.0f%% of it was silence and the longest pause was %.1fs. "
                                                .formatted(metrics.silenceRatio() * 100, metrics.longestSilenceSeconds())
                                        + "Pace and hesitation need a transcript, so they are not included."),
                        new DimensionScore(
                                Dimension.TASK_FULFILLMENT,
                                fulfillment,
                                "Measured from your recording: you used %d%% of the %d seconds available. "
                                                .formatted(metrics.timeUsedPercent(), metrics.allowedSeconds())
                                        + "Whether you answered the question needs a transcript.")),
                strengths(metrics),
                improvements(metrics),
                // Nothing of the user's was heard, so there is nothing to correct.
                List.of(),
                sampleAnswer(),
                "Set OPENAI_API_KEY and restart with APP_CONTENT_MODE=LIVE to have this answer "
                        + "transcribed and assessed properly. Until then, use the timing above.",
                "seed:speaking-scorer-v2");
    }

    /** A transcript exists, so word-derived measurements can be reported too. */
    private Assessment withTranscript(DeliveryMetrics metrics) {
        int fulfillment = scoreTimeUsed(metrics);
        int listenability = scorePausing(metrics);

        return new Assessment(
                null,
                Confidence.LOW,
                List.of(
                        new DimensionScore(Dimension.CONTENT_COHERENCE, null, NOT_ASSESSED),
                        new DimensionScore(Dimension.VOCABULARY, null, NOT_ASSESSED),
                        new DimensionScore(
                                Dimension.LISTENABILITY,
                                listenability,
                                "Measured from your recording: %.0f words per minute, %d filler(s), %.0f%% silence."
                                        .formatted(
                                                metrics.wordsPerMinute(),
                                                metrics.fillerCount(),
                                                metrics.silenceRatio() * 100)),
                        new DimensionScore(
                                Dimension.TASK_FULFILLMENT,
                                fulfillment,
                                "Measured from your recording: %d words in %d%% of the time available."
                                        .formatted(metrics.wordCount(), metrics.timeUsedPercent()))),
                strengths(metrics),
                improvements(metrics),
                List.of(),
                sampleAnswer(),
                "Record the same task again and aim to use at least 90 percent of the time, "
                        + "giving two clear reasons and one concession.",
                "seed:speaking-scorer-v2");
    }

    private static String sampleAnswer() {
        // A model answer to the task, never described as a rewrite of the user's
        // answer, because in demo mode nothing has read their answer.
        return "A strong answer to a task like this states a position in the first sentence, gives two "
                + "reasons with a specific detail behind each, acknowledges the strongest objection, and "
                + "closes by restating the recommendation.";
    }

    /** Using the time is the clearest thing a metric alone can say about task fulfilment. */
    private int scoreTimeUsed(DeliveryMetrics metrics) {
        int used = metrics.timeUsedPercent();
        if (used >= 85) return 9;
        if (used >= 70) return 7;
        if (used >= 50) return 5;
        if (used >= 30) return 3;
        return 2;
    }

    private int scorePausing(DeliveryMetrics metrics) {
        int score = 8;
        if (metrics.silenceRatio() > 0.35) {
            score -= 2;
        }
        if (metrics.longestSilenceSeconds() > 4) {
            score -= 1;
        }
        return Math.max(1, score);
    }

    private List<String> strengths(DeliveryMetrics metrics) {
        List<String> strengths = new ArrayList<>();
        if (metrics.timeUsedPercent() >= 70) {
            strengths.add("You used most of the time available, which gives a listener enough to work with.");
        }
        if (metrics.silenceRatio() <= 0.25) {
            strengths.add("You kept going without long gaps, which is what makes an answer easy to follow.");
        }
        strengths.add("You completed a full attempt under time pressure, which is the habit that matters most.");
        strengths.add("You submitted a recording within the time limit, which is what practice is for.");
        return List.copyOf(strengths.subList(0, 2));
    }

    private List<Improvement> improvements(DeliveryMetrics metrics) {
        List<Improvement> improvements = new ArrayList<>();

        if (metrics.usedTimePoorly()) {
            improvements.add(new Improvement(
                    "You finished well before the time was up.",
                    "Unused time reads as having run out of things to say.",
                    "Plan three points during preparation and give each one a sentence of support."));
        }
        if (metrics.silenceRatio() > 0.3 || metrics.longestSilenceSeconds() > 4) {
            improvements.add(new Improvement(
                    "There were long pauses in the middle of the answer.",
                    "Long silences break the listener's thread and cost you time.",
                    "When you lose the thread, restate the question in your own words and continue from there."));
        }
        improvements.add(new Improvement(
                "Demo mode cannot tell you anything about your language.",
                "Content, vocabulary, and accuracy are the parts that move a score, and none of them "
                        + "were assessed here.",
                "Configure an OpenAI key to get a real assessment of what you said."));
        improvements.add(new Improvement(
                "Signal the structure of your answer.",
                "A listener who cannot tell how many points are coming has to work harder.",
                "Open with \"There are two reasons\" and then mark each one."));
        return List.copyOf(improvements.subList(0, 2));
    }
}
