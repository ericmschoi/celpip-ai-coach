package com.listenspeak.coach.speaking.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Compares a real transcript against a manually prepared verbatim reference.
 *
 * <p>This exists because "the transcription looked fine" is not a measurement.
 * A model that quietly removes every "um" produces a perfectly readable
 * transcript and silently destroys the filler count this app reports, and only
 * a reference comparison catches that.
 *
 * <p>Normalisation deliberately keeps filler words and repetitions: they are the
 * point of the comparison, not noise to be stripped.
 */
public final class TranscriptAccuracy {

    private static final Pattern WORD = Pattern.compile("[a-z']+");

    /** The fillers whose survival decides whether the delivery metrics mean anything. */
    private static final List<String> FILLERS =
            List.of("um", "uh", "erm", "er", "ah", "hmm", "mm", "like", "you", "know", "i", "mean");

    private TranscriptAccuracy() {}

    /**
     * @param wordErrorRate Levenshtein distance over reference length, 0 is perfect
     * @param fillerRecall proportion of reference filler tokens present in the result
     * @param repetitionRecall proportion of reference immediate repetitions preserved
     */
    public record Report(
            int referenceWords,
            int recognisedWords,
            int substitutions,
            int deletions,
            int insertions,
            double wordErrorRate,
            double fillerRecall,
            int referenceFillers,
            int recognisedFillers,
            double repetitionRecall,
            int referenceRepetitions,
            int recognisedRepetitions,
            List<String> missingWords,
            List<String> unexpectedWords) {

        public String summary() {
            return """
                    words: %d reference, %d recognised
                    edits: %d substitutions, %d deletions, %d insertions
                    word error rate: %.1f%%
                    filler recall: %.0f%% (%d of %d kept)
                    repetition recall: %.0f%% (%d of %d kept)
                    """
                    .formatted(
                            referenceWords,
                            recognisedWords,
                            substitutions,
                            deletions,
                            insertions,
                            wordErrorRate * 100,
                            fillerRecall * 100,
                            recognisedFillers,
                            referenceFillers,
                            repetitionRecall * 100,
                            recognisedRepetitions,
                            referenceRepetitions);
        }
    }

    public static Report compare(String reference, String recognised) {
        List<String> referenceWords = tokenize(reference);
        List<String> recognisedWords = tokenize(recognised);

        Alignment alignment = align(referenceWords, recognisedWords);

        int errors = alignment.substitutions + alignment.deletions + alignment.insertions;
        double wer = referenceWords.isEmpty() ? 0 : (double) errors / referenceWords.size();

        int referenceFillers = countFillers(referenceWords);
        int recognisedFillers = countFillers(recognisedWords);
        int referenceRepetitions = countRepetitions(referenceWords);
        int recognisedRepetitions = countRepetitions(recognisedWords);

        return new Report(
                referenceWords.size(),
                recognisedWords.size(),
                alignment.substitutions,
                alignment.deletions,
                alignment.insertions,
                wer,
                referenceFillers == 0 ? 1 : Math.min(1, (double) recognisedFillers / referenceFillers),
                referenceFillers,
                recognisedFillers,
                referenceRepetitions == 0 ? 1 : Math.min(1, (double) recognisedRepetitions / referenceRepetitions),
                referenceRepetitions,
                recognisedRepetitions,
                missing(referenceWords, recognisedWords),
                missing(recognisedWords, referenceWords));
    }

    /** Lower-cased alphabetic tokens. Fillers and repeats are kept on purpose. */
    static List<String> tokenize(String text) {
        return WORD.matcher(text.toLowerCase(Locale.ROOT))
                .results()
                .map(match -> match.group())
                .toList();
    }

    private record Alignment(int substitutions, int deletions, int insertions) {}

    /** Standard Levenshtein alignment with edit-type accounting. */
    private static Alignment align(List<String> reference, List<String> hypothesis) {
        int rows = reference.size() + 1;
        int columns = hypothesis.size() + 1;
        int[][] cost = new int[rows][columns];

        for (int row = 0; row < rows; row++) {
            cost[row][0] = row;
        }
        for (int column = 0; column < columns; column++) {
            cost[0][column] = column;
        }

        for (int row = 1; row < rows; row++) {
            for (int column = 1; column < columns; column++) {
                int substitute = cost[row - 1][column - 1]
                        + (reference.get(row - 1).equals(hypothesis.get(column - 1)) ? 0 : 1);
                cost[row][column] = Math.min(substitute, Math.min(cost[row - 1][column] + 1, cost[row][column - 1] + 1));
            }
        }

        int substitutions = 0;
        int deletions = 0;
        int insertions = 0;
        int row = reference.size();
        int column = hypothesis.size();

        while (row > 0 || column > 0) {
            if (row > 0
                    && column > 0
                    && cost[row][column]
                            == cost[row - 1][column - 1]
                                    + (reference.get(row - 1).equals(hypothesis.get(column - 1)) ? 0 : 1)) {
                if (!reference.get(row - 1).equals(hypothesis.get(column - 1))) {
                    substitutions++;
                }
                row--;
                column--;
            } else if (row > 0 && cost[row][column] == cost[row - 1][column] + 1) {
                deletions++;
                row--;
            } else {
                insertions++;
                column--;
            }
        }
        return new Alignment(substitutions, deletions, insertions);
    }

    private static int countFillers(List<String> words) {
        return (int) words.stream().filter(FILLERS::contains).count();
    }

    /** Immediate repetitions such as "I I think", which signal a false start. */
    private static int countRepetitions(List<String> words) {
        int repetitions = 0;
        for (int i = 1; i < words.size(); i++) {
            if (words.get(i).equals(words.get(i - 1))) {
                repetitions++;
            }
        }
        return repetitions;
    }

    /** Words present in the first list more often than in the second, with counts. */
    private static List<String> missing(List<String> from, List<String> in) {
        Map<String, Long> fromCounts = from.stream().collect(Collectors.groupingBy(w -> w, Collectors.counting()));
        Map<String, Long> inCounts = in.stream().collect(Collectors.groupingBy(w -> w, Collectors.counting()));

        List<String> result = new ArrayList<>();
        fromCounts.forEach((word, count) -> {
            long other = inCounts.getOrDefault(word, 0L);
            if (count > other) {
                result.add("%s x%d".formatted(word, count - other));
            }
        });
        result.sort(String::compareTo);
        return List.copyOf(result);
    }
}
