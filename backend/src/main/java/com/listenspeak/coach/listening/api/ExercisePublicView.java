package com.listenspeak.coach.listening.api;

import com.listenspeak.coach.listening.Difficulty;
import com.listenspeak.coach.listening.domain.ListeningExercise;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the browser is allowed to see <em>before</em> submission.
 *
 * <p>This type has no {@code speakerTurns}, {@code correctOptionId},
 * {@code explanation}, or {@code evidence} field. Secrecy is a property of the
 * type: there is nothing to omit at serialization time, nothing to hide with
 * CSS, and no client state that could be tampered with to reveal it.
 *
 * <p><strong>Do not add answer-bearing fields here.</strong> They belong on
 * {@link SubmissionResultView}.
 */
public record ExercisePublicView(
        UUID id,
        int part,
        String partLabel,
        Difficulty difficulty,
        String title,
        String scenario,
        List<String> speakers,
        int questionCount,
        List<QuestionView> questions,
        String audioUrl,
        int audioDurationSeconds,
        String audioDisclosure,
        Instant createdAt) {

    /** A question as the user sees it while practising: stem and four options. */
    public record QuestionView(String id, String stem, List<OptionView> options) {}

    public record OptionView(String id, String text) {}

    public static final String AI_VOICE_DISCLOSURE = "This exercise uses AI-generated voices.";

    public static ExercisePublicView of(ListeningExercise exercise, String audioUrl) {
        List<String> speakers = exercise.speakerTurns().stream()
                .map(turn -> turn.speakerDisplayName())
                .distinct()
                .toList();

        List<QuestionView> questions = exercise.questions().stream()
                .map(question -> new QuestionView(
                        question.id(),
                        question.stem(),
                        question.options().stream()
                                .map(option -> new OptionView(option.id(), option.text()))
                                .toList()))
                .toList();

        return new ExercisePublicView(
                exercise.id(),
                exercise.part().number(),
                exercise.part().label(),
                exercise.difficulty(),
                exercise.title(),
                exercise.scenario(),
                speakers,
                questions.size(),
                questions,
                audioUrl,
                exercise.audioDurationSeconds(),
                AI_VOICE_DISCLOSURE,
                exercise.createdAt());
    }
}
