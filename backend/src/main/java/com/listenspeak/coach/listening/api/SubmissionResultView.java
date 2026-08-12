package com.listenspeak.coach.listening.api;

import com.listenspeak.coach.listening.domain.ListeningAttempt;
import com.listenspeak.coach.listening.domain.ListeningExercise;
import com.listenspeak.coach.listening.domain.Question;
import com.listenspeak.coach.listening.domain.Skill;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Everything the user earns by submitting: the score, why each answer was right
 * or wrong, the evidence from the dialogue, the full transcript, and one
 * targeted tip. This is the only type that carries the answer key.
 */
public record SubmissionResultView(
        UUID attemptId,
        UUID exerciseId,
        int correctCount,
        int totalQuestions,
        int scorePercent,
        List<QuestionResultView> results,
        List<TranscriptLineView> transcript,
        String tip,
        Skill weakestSkill,
        Instant submittedAt) {

    public record QuestionResultView(
            String questionId,
            String stem,
            String selectedOptionId,
            String correctOptionId,
            String correctOptionText,
            boolean correct,
            String explanation,
            String evidence,
            Skill skill) {}

    public record TranscriptLineView(String speaker, String text) {}

    public static SubmissionResultView of(ListeningExercise exercise, ListeningAttempt attempt, String fallbackTip) {
        Map<String, Question> questions =
                exercise.questions().stream().collect(Collectors.toMap(Question::id, Function.identity()));

        List<QuestionResultView> results = attempt.results().stream()
                .map(result -> {
                    Question question = questions.get(result.questionId());
                    return new QuestionResultView(
                            result.questionId(),
                            question.stem(),
                            result.selectedOptionId(),
                            question.correctOptionId(),
                            question.correctOption().text(),
                            result.correct(),
                            question.explanation(),
                            question.evidence(),
                            question.skill());
                })
                .toList();

        List<TranscriptLineView> transcript = exercise.speakerTurns().stream()
                .map(turn -> new TranscriptLineView(turn.speakerDisplayName(), turn.text()))
                .toList();

        return new SubmissionResultView(
                attempt.id(),
                exercise.id(),
                attempt.correctCount(),
                attempt.totalQuestions(),
                attempt.scorePercent(),
                results,
                transcript,
                tipFor(attempt, fallbackTip),
                attempt.weakestSkill(),
                attempt.submittedAt());
    }

    /**
     * A perfect score gets the exercise's own general tip; otherwise the tip is
     * chosen from the skill the user actually missed most.
     */
    private static String tipFor(ListeningAttempt attempt, String fallbackTip) {
        if (attempt.weakestSkill() != null) {
            return attempt.weakestSkill().tip();
        }
        return fallbackTip != null
                ? fallbackTip
                : "Full marks. Try the next difficulty up, or the same part with a shorter listening window.";
    }
}
