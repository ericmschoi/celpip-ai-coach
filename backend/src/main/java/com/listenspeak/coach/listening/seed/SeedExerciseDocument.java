package com.listenspeak.coach.listening.seed;

import com.listenspeak.coach.listening.Difficulty;
import com.listenspeak.coach.listening.domain.AnswerOption;
import com.listenspeak.coach.listening.domain.GeneratedExercise;
import com.listenspeak.coach.listening.domain.Part;
import com.listenspeak.coach.listening.domain.Question;
import com.listenspeak.coach.listening.domain.Skill;
import com.listenspeak.coach.listening.domain.SpeakerTurn;
import java.util.List;

/**
 * On-disk shape of a seed fixture. Deliberately separate from the domain model
 * so the JSON format can change without dragging the domain with it.
 */
public record SeedExerciseDocument(
        String seedId,
        String title,
        String scenario,
        int part,
        Difficulty difficulty,
        String audioResource,
        int audioDurationSeconds,
        List<TurnDocument> speakerTurns,
        List<QuestionDocument> questions,
        String listeningTip) {

    public record TurnDocument(String speakerId, String speakerDisplayName, String text, int pauseAfterMs) {}

    public record OptionDocument(String id, String text) {}

    public record QuestionDocument(
            String id,
            String stem,
            List<OptionDocument> options,
            String correctOptionId,
            String explanation,
            String evidence,
            Skill skill) {}

    public GeneratedExercise toGeneratedExercise() {
        List<SpeakerTurn> turns = speakerTurns.stream()
                .map(turn -> new SpeakerTurn(
                        turn.speakerId(), turn.speakerDisplayName(), turn.text(), turn.pauseAfterMs()))
                .toList();

        List<Question> mappedQuestions = questions.stream()
                .map(question -> new Question(
                        question.id(),
                        question.stem(),
                        question.options().stream()
                                .map(option -> new AnswerOption(option.id(), option.text()))
                                .toList(),
                        question.correctOptionId(),
                        question.explanation(),
                        question.evidence(),
                        question.skill()))
                .toList();

        return new GeneratedExercise(title, scenario, Part.ofNumber(part), difficulty, turns, mappedQuestions);
    }
}
