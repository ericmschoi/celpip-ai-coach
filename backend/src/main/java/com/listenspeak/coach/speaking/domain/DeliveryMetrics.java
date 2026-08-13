package com.listenspeak.coach.speaking.domain;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Coarse, locally computed measurements of how the answer was delivered.
 *
 * <p>These are deliberately simple and honest. They describe pace, hesitation,
 * and use of the time — they are <strong>not</strong> a pronunciation
 * assessment, and the scoring prompt is told not to treat them as one.
 *
 * @param durationSeconds measured length of the recording
 * @param allowedSeconds the task's time limit
 * @param wordCount words in the transcript
 * @param wordsPerMinute pace over the speaking time
 * @param fillerCount recognised filler words in the transcript
 * @param repeatedStarts immediate word repetitions such as "I I think"
 * @param silenceRatio fraction of the recording below the silence threshold
 * @param longestSilenceSeconds longest single pause
 */
public record DeliveryMetrics(
        double durationSeconds,
        int allowedSeconds,
        int wordCount,
        double wordsPerMinute,
        int fillerCount,
        int repeatedStarts,
        double silenceRatio,
        double longestSilenceSeconds,
        /** Null when the timing pass produced no word timestamps. Never zero-filled. */
        TimingMetrics timing) {

    /** Common English fillers. Kept short on purpose: a long list produces false positives. */
    private static final List<String> FILLERS =
            List.of("um", "uh", "erm", "ah", "hmm", "like", "you know", "i mean", "sort of", "kind of");

    private static final Pattern WORD = Pattern.compile("[a-z']+");

    /** True when the speaker used well under the time available. */
    public boolean usedTimePoorly() {
        return durationSeconds < allowedSeconds * 0.6;
    }

    public int timeUsedPercent() {
        return allowedSeconds == 0 ? 0 : (int) Math.round(durationSeconds * 100 / allowedSeconds);
    }

    public static DeliveryMetrics of(
            String transcript,
            double durationSeconds,
            int allowedSeconds,
            double silenceRatio,
            double longestSilenceSeconds) {
        return of(transcript, durationSeconds, allowedSeconds, silenceRatio, longestSilenceSeconds, null);
    }

    public static DeliveryMetrics of(
            String transcript,
            double durationSeconds,
            int allowedSeconds,
            double silenceRatio,
            double longestSilenceSeconds,
            TimingMetrics timing) {

        String normalized = transcript.toLowerCase(Locale.ROOT);
        List<String> words = WORD.matcher(normalized).results().map(match -> match.group()).toList();

        double speakingSeconds = Math.max(1, durationSeconds * (1 - silenceRatio));
        double wordsPerMinute = words.size() / speakingSeconds * 60;

        return new DeliveryMetrics(
                round(durationSeconds),
                allowedSeconds,
                words.size(),
                round(wordsPerMinute),
                countFillers(normalized, words),
                countRepeatedStarts(words),
                round(silenceRatio),
                round(longestSilenceSeconds),
                timing);
    }

    private static int countFillers(String normalized, List<String> words) {
        int count = 0;
        for (String filler : FILLERS) {
            if (filler.contains(" ")) {
                count += countOccurrences(normalized, filler);
            } else {
                count += (int) words.stream().filter(filler::equals).count();
            }
        }
        return count;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static int countRepeatedStarts(List<String> words) {
        int repeats = 0;
        for (int i = 1; i < words.size(); i++) {
            if (words.get(i).equals(words.get(i - 1)) && words.get(i).length() > 1) {
                repeats++;
            }
        }
        return repeats;
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    /** Compact, human-readable form handed to the scoring model as evidence. */
    public String summary() {
        return """
                duration: %.1fs of %ds allowed (%d%% of the time used)
                words: %d
                pace: %.0f words per minute while speaking
                fillers: %d
                repeated word starts: %d
                silence: %.0f%% of the recording, longest pause %.1fs
                """
                .formatted(
                        durationSeconds,
                        allowedSeconds,
                        timeUsedPercent(),
                        wordCount,
                        wordsPerMinute,
                        fillerCount,
                        repeatedStarts,
                        silenceRatio * 100,
                        longestSilenceSeconds)
                + (timing == null
                        ? "word timestamps: unavailable, so pause locations were not measured\n"
                        : timing.summary());
    }
}
