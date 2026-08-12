package com.listenspeak.coach.listening.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One submitted set of answers. Scoring is deterministic and happens on the
 * server from the persisted exercise, so a client cannot influence its score by
 * sending anything other than option ids.
 */
public record ListeningAttempt(
        UUID id,
        UUID exerciseId,
        String ownerId,
        List<QuestionResult> results,
        int correctCount,
        int totalQuestions,
        Skill weakestSkill,
        Instant submittedAt) {

    public record QuestionResult(
            String questionId, String selectedOptionId, String correctOptionId, boolean correct, Skill skill) {}

    public ListeningAttempt {
        results = List.copyOf(results);
    }

    /** Whole-number percentage, which is all the UI shows. */
    public int scorePercent() {
        return totalQuestions == 0 ? 0 : Math.round(correctCount * 100f / totalQuestions);
    }

    public static ListeningAttempt score(
            ListeningExercise exercise, String ownerId, Map<String, String> selections, Instant submittedAt) {

        List<QuestionResult> results = exercise.questions().stream()
                .map(question -> {
                    String selected = selections.get(question.id());
                    return new QuestionResult(
                            question.id(),
                            selected,
                            question.correctOptionId(),
                            question.isCorrect(selected),
                            question.skill());
                })
                .toList();

        int correct = (int) results.stream().filter(QuestionResult::correct).count();

        return new ListeningAttempt(
                UUID.randomUUID(),
                exercise.id(),
                ownerId,
                results,
                correct,
                exercise.questions().size(),
                weakestSkill(results),
                submittedAt);
    }

    /**
     * The skill with the most misses drives the single tip shown afterwards.
     * Ties resolve by enum order so the same answers always produce the same
     * tip.
     */
    private static Skill weakestSkill(List<QuestionResult> results) {
        return results.stream()
                .filter(result -> !result.correct())
                .collect(java.util.stream.Collectors.groupingBy(
                        QuestionResult::skill, java.util.stream.Collectors.counting()))
                .entrySet()
                .stream()
                .max(java.util.Comparator.<Map.Entry<Skill, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(entry -> entry.getKey().ordinal(), java.util.Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
