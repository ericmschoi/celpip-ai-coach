package com.listenspeak.coach.speaking.domain;

import com.listenspeak.coach.speaking.evaluation.TimingAnalysis;
import java.util.List;
import java.util.Locale;

/**
 * Measurements that can only be computed from word timestamps.
 *
 * <p>Kept separate from {@link DeliveryMetrics} so that losing the timing pass
 * makes these <em>absent</em> rather than zero. A pace of "0 wpm" reads as a
 * measurement of a silent speaker; the absence of a pace reads as what it is.
 *
 * @param speakingSeconds time actually spent producing words, excluding gaps
 * @param wordsPerMinute pace over speaking time rather than wall-clock time
 * @param pauseCount gaps between words at or above {@link #PAUSE_THRESHOLD_SECONDS}
 * @param longestPauseSeconds longest gap between two words
 * @param fillerOccurrences where each recognised filler fell, for the live report
 */
public record TimingMetrics(
        double speakingSeconds,
        double wordsPerMinute,
        int wordCount,
        int pauseCount,
        double longestPauseSeconds,
        List<FillerOccurrence> fillerOccurrences) {

    /** A gap this long between words reads as a pause rather than normal articulation. */
    public static final double PAUSE_THRESHOLD_SECONDS = 0.6;

    private static final List<String> FILLERS = List.of("um", "uh", "er", "erm", "mm", "hmm", "ah");

    public record FillerOccurrence(String word, double atSeconds) {}

    public TimingMetrics {
        fillerOccurrences = List.copyOf(fillerOccurrences);
    }

    /** Null when the timing pass did not produce word timestamps. */
    public static TimingMetrics from(TimingAnalysis timing) {
        if (!timing.hasWordTimestamps()) {
            return null;
        }

        List<TimingAnalysis.TimedWord> words = timing.words();

        double speakingSeconds = words.stream()
                .mapToDouble(TimingAnalysis.TimedWord::durationSeconds)
                .sum();

        int pauseCount = 0;
        double longestPause = 0;
        for (int i = 1; i < words.size(); i++) {
            double gap = words.get(i).startSeconds() - words.get(i - 1).endSeconds();
            if (gap >= PAUSE_THRESHOLD_SECONDS) {
                pauseCount++;
            }
            longestPause = Math.max(longestPause, Math.max(0, gap));
        }

        List<FillerOccurrence> fillers = words.stream()
                .filter(word -> FILLERS.contains(normalize(word.word())))
                .map(word -> new FillerOccurrence(normalize(word.word()), round(word.startSeconds())))
                .toList();

        double pace = speakingSeconds <= 0 ? 0 : words.size() / speakingSeconds * 60;

        return new TimingMetrics(
                round(speakingSeconds), round(pace), words.size(), pauseCount, round(longestPause), fillers);
    }

    private static String normalize(String word) {
        return word.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    /** Compact form for the scoring prompt. */
    public String summary() {
        return """
                speaking time: %.1fs of actual word production
                pace from timestamps: %.0f words per minute while speaking
                pauses of %.1fs or longer: %d, longest %.1fs
                fillers located in time: %d
                """
                .formatted(
                        speakingSeconds,
                        wordsPerMinute,
                        PAUSE_THRESHOLD_SECONDS,
                        pauseCount,
                        longestPauseSeconds,
                        fillerOccurrences.size());
    }
}
