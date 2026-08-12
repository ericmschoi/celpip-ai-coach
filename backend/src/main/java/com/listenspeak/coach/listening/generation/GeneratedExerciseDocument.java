package com.listenspeak.coach.listening.generation;

import com.listenspeak.coach.listening.Difficulty;
import com.listenspeak.coach.listening.domain.AnswerOption;
import com.listenspeak.coach.listening.domain.GeneratedExercise;
import com.listenspeak.coach.listening.domain.Part;
import com.listenspeak.coach.listening.domain.Question;
import com.listenspeak.coach.listening.domain.Skill;
import com.listenspeak.coach.listening.domain.SpeakerTurn;
import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import java.util.List;

/**
 * Binding target for the model's JSON. Kept separate from the domain model so a
 * malformed field fails here, with a clear message, instead of throwing from a
 * domain constructor deep in the call stack.
 */
public record GeneratedExerciseDocument(
        String title,
        String scenario,
        String listeningTip,
        List<TurnDocument> speakerTurns,
        List<QuestionDocument> questions) {

    public record TurnDocument(String speakerId, String speakerDisplayName, String text, Integer pauseAfterMs) {}

    public record OptionDocument(String id, String text) {}

    public record QuestionDocument(
            String id,
            String stem,
            List<OptionDocument> options,
            String correctOptionId,
            String explanation,
            String evidence,
            String skill) {}

    /** Default gap between turns when the model gives an implausible one. */
    private static final int DEFAULT_PAUSE_MS = 350;

    private static final int MAX_PAUSE_MS = 1200;

    public GeneratedExercise toDomain(Part part, Difficulty difficulty) {
        try {
            List<SpeakerTurn> turns = speakerTurns.stream()
                    .map(turn -> new SpeakerTurn(
                            turn.speakerId(),
                            turn.speakerDisplayName() == null ? turn.speakerId() : turn.speakerDisplayName(),
                            turn.text() == null ? "" : turn.text().trim(),
                            clampPause(turn.pauseAfterMs())))
                    .toList();

            List<Question> mapped = questions.stream()
                    .map(GeneratedExerciseDocument::toQuestion)
                    .toList();

            return new GeneratedExercise(title, scenario, part, difficulty, turns, mapped);

        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(
                    ErrorCode.GENERATION_INVALID,
                    "The generated exercise was not usable. Generating a new one usually works.",
                    e);
        }
    }

    private static Question toQuestion(QuestionDocument document) {
        List<AnswerOption> options = document.options().stream()
                .map(option -> new AnswerOption(option.id(), option.text().trim()))
                .toList();

        return new Question(
                document.id(),
                document.stem().trim(),
                options,
                document.correctOptionId(),
                document.explanation().trim(),
                document.evidence().trim(),
                Skill.valueOf(document.skill()));
    }

    private static int clampPause(Integer pauseAfterMs) {
        if (pauseAfterMs == null || pauseAfterMs < 0) {
            return DEFAULT_PAUSE_MS;
        }
        return Math.min(pauseAfterMs, MAX_PAUSE_MS);
    }
}
