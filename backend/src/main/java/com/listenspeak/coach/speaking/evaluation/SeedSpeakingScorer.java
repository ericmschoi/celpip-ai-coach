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
 * Demo-mode scorer. Deterministic and rule-based, driven by the delivery
 * metrics that were actually measured from the user's recording, so the numbers
 * respond to how long they really spoke rather than being a constant.
 *
 * <p>Confidence is always {@code LOW}: no language model looked at this, and
 * pretending otherwise would be dishonest.
 */
@Component
public class SeedSpeakingScorer implements SpeakingScorer {

    @Override
    public ContentMode mode() {
        return ContentMode.SEED;
    }

    @Override
    public Assessment score(SpeakingTask task, String promptText, String transcript, DeliveryMetrics metrics) {
        int fulfillment = scoreTaskFulfillment(metrics);
        int listenability = scoreListenability(metrics);
        int vocabulary = scoreVocabulary(metrics);
        int coherence = Math.max(1, Math.round((fulfillment + listenability + vocabulary) / 3f));

        int level = Math.round((fulfillment + listenability + vocabulary + coherence) / 4f);

        return new Assessment(
                level,
                Confidence.LOW,
                List.of(
                        new DimensionScore(
                                Dimension.CONTENT_COHERENCE,
                                coherence,
                                "Demo mode does not analyse content. This score is derived from your delivery "
                                        + "measurements only."),
                        new DimensionScore(
                                Dimension.VOCABULARY,
                                vocabulary,
                                "Based on %d words in %.0f seconds.".formatted(metrics.wordCount(), metrics.durationSeconds())),
                        new DimensionScore(
                                Dimension.LISTENABILITY,
                                listenability,
                                "Pace was %.0f words per minute with %d filler(s) and %.0f%% silence."
                                        .formatted(
                                                metrics.wordsPerMinute(),
                                                metrics.fillerCount(),
                                                metrics.silenceRatio() * 100)),
                        new DimensionScore(
                                Dimension.TASK_FULFILLMENT,
                                fulfillment,
                                "You used %d%% of the %d seconds available."
                                        .formatted(metrics.timeUsedPercent(), metrics.allowedSeconds()))),
                strengths(metrics),
                improvements(metrics),
                List.of(new Correction(
                        "I think she should probably take the promotion",
                        "I would encourage her to take the promotion",
                        "A direct recommendation is stronger than a hedged one when the task asks for advice.")),
                """
                I would take the promotion, but I would not rush it. The salary increase is real and \
                an opportunity like this does not come around often. The difficulty is her family, \
                especially if her parents need help. So my advice is to accept, and ask the company \
                for a six-month trial before selling the house. That way she keeps her options open. \
                The hockey team matters less, because she can join a new one wherever she lands.
                """
                        .replaceAll("\\s+", " ")
                        .trim(),
                "Record the same task again and aim to use at least 90 percent of the time, giving two "
                        + "clear reasons and one concession.",
                "seed:speaking-scorer-v1");
    }

    /** Using the time is the clearest thing a metric can tell us about task fulfilment. */
    private int scoreTaskFulfillment(DeliveryMetrics metrics) {
        int used = metrics.timeUsedPercent();
        if (used >= 85) return 9;
        if (used >= 70) return 7;
        if (used >= 50) return 5;
        if (used >= 30) return 3;
        return 2;
    }

    private int scoreListenability(DeliveryMetrics metrics) {
        int score = 8;
        double pace = metrics.wordsPerMinute();
        if (pace < 90 || pace > 190) {
            score -= 2;
        }
        if (metrics.silenceRatio() > 0.35) {
            score -= 2;
        }
        if (metrics.longestSilenceSeconds() > 4) {
            score -= 1;
        }
        if (metrics.wordCount() > 0 && metrics.fillerCount() * 100.0 / metrics.wordCount() > 6) {
            score -= 1;
        }
        return Math.max(1, score);
    }

    private int scoreVocabulary(DeliveryMetrics metrics) {
        if (metrics.wordCount() >= 160) return 8;
        if (metrics.wordCount() >= 100) return 7;
        if (metrics.wordCount() >= 60) return 5;
        if (metrics.wordCount() >= 25) return 3;
        return 2;
    }

    private List<String> strengths(DeliveryMetrics metrics) {
        List<String> strengths = new ArrayList<>();
        if (metrics.timeUsedPercent() >= 70) {
            strengths.add("You used most of the time available, which gives a listener enough to work with.");
        }
        if (metrics.wordsPerMinute() >= 110 && metrics.wordsPerMinute() <= 170) {
            strengths.add("Your pace was in a comfortable range to follow.");
        }
        // Always exactly two, whatever the metrics happened to show.
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
        if (metrics.wordCount() > 0 && metrics.fillerCount() * 100.0 / metrics.wordCount() > 5) {
            improvements.add(new Improvement(
                    "Fillers appeared often.",
                    "They make an otherwise clear answer harder to follow.",
                    "Replace a filler with a short silent pause; a pause is far less noticeable."));
        }
        // Always exactly two, whatever the metrics happened to show.
        improvements.add(new Improvement(
                "The answer stated a position but supported it lightly.",
                "Assessors look for reasons, not just a conclusion.",
                "For each point, add one sentence beginning \"because\" or \"which means\"."));
        improvements.add(new Improvement(
                "The answer did not signal its structure.",
                "A listener who cannot tell how many points are coming has to work harder.",
                "Open with \"There are two reasons\" and then mark each one."));
        return List.copyOf(improvements.subList(0, 2));
    }
}
