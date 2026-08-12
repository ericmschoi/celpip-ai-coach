package com.listenspeak.coach.listening.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.listenspeak.coach.listening.Difficulty;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The validator is the gate between a model's output and a user's practice
 * session, so each rule gets a test that breaks exactly that rule.
 */
class ExerciseValidatorTest {

    private final ExerciseValidator validator = new ExerciseValidator();

    @Test
    void acceptsAWellFormedExercise() {
        ExerciseValidator.Result result = validator.validate(ExerciseFixtures.validPart5());

        assertThat(result.errors()).isEmpty();
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void rejectsAnyQuestionCountOtherThanSix() {
        GeneratedExercise fiveQuestions = ExerciseFixtures.withQuestions(
                ExerciseFixtures.validPart5(),
                ExerciseFixtures.questions().subList(0, 5));

        assertThat(validator.validate(fiveQuestions).summary()).contains("Exactly 6 questions are required");
    }

    @Test
    void rejectsAQuestionWithoutFourOptions() {
        Question threeOptions = new Question(
                "q6",
                "What is the final arrangement?",
                List.of(
                        new AnswerOption("A", "Tuesday morning"),
                        new AnswerOption("B", "Thursday at one"),
                        new AnswerOption("C", "Friday afternoon")),
                "B",
                "Explanation",
                "book the room for Thursday at one",
                Skill.FINAL_POSITION);

        List<Question> questions = new ArrayList<>(ExerciseFixtures.questions().subList(0, 5));
        questions.add(threeOptions);

        assertThat(validator
                        .validate(ExerciseFixtures.withQuestions(ExerciseFixtures.validPart5(), questions))
                        .summary())
                .contains("exactly 4 options are required");
    }

    @Test
    void rejectsDuplicateOptionText() {
        Question duplicated = new Question(
                "q6",
                "What is the final arrangement?",
                List.of(
                        new AnswerOption("A", "Thursday at one"),
                        new AnswerOption("B", "Thursday at one"),
                        new AnswerOption("C", "Friday afternoon"),
                        new AnswerOption("D", "Tuesday morning")),
                "B",
                "Explanation",
                "book the room for Thursday at one",
                Skill.FINAL_POSITION);

        List<Question> questions = new ArrayList<>(ExerciseFixtures.questions().subList(0, 5));
        questions.add(duplicated);

        assertThat(validator
                        .validate(ExerciseFixtures.withQuestions(ExerciseFixtures.validPart5(), questions))
                        .summary())
                .contains("options must be distinct");
    }

    @Test
    void rejectsAnAnswerThatNamesAnOptionTheQuestionDoesNotHave() {
        Question pointingNowhere = new Question(
                "q6",
                "What is the final arrangement?",
                List.of(
                        new AnswerOption("A", "Tuesday morning"),
                        new AnswerOption("B", "Thursday at one"),
                        new AnswerOption("C", "Friday afternoon"),
                        new AnswerOption("D", "No fixed slot")),
                "E",
                "Explanation",
                "book the room for Thursday at one",
                Skill.FINAL_POSITION);

        List<Question> questions = new ArrayList<>(ExerciseFixtures.questions().subList(0, 5));
        questions.add(pointingNowhere);

        assertThat(validator
                        .validate(ExerciseFixtures.withQuestions(ExerciseFixtures.validPart5(), questions))
                        .summary())
                .contains("correctOptionId must name one of this question's options");
    }

    @Test
    void rejectsEvidenceThatIsNotInTheTranscript() {
        List<Question> questions = new ArrayList<>(ExerciseFixtures.questions().subList(0, 5));
        questions.add(ExerciseFixtures.question(
                "q6",
                "What is the final arrangement?",
                "B",
                "the committee approved additional funding for translation services",
                Skill.FINAL_POSITION));

        assertThat(validator
                        .validate(ExerciseFixtures.withQuestions(ExerciseFixtures.validPart5(), questions))
                        .summary())
                .contains("the answer is not supportable");
    }

    @Test
    void rejectsDuplicateQuestions() {
        List<Question> questions = new ArrayList<>(ExerciseFixtures.questions().subList(0, 5));
        questions.add(ExerciseFixtures.question(
                "q6", "When must the decision be made?", "B", "we have until Friday to decide", Skill.DETAIL));

        assertThat(validator
                        .validate(ExerciseFixtures.withQuestions(ExerciseFixtures.validPart5(), questions))
                        .summary())
                .contains("duplicates another question");
    }

    @Test
    void rejectsContentThatCallsItselfOfficial() {
        GeneratedExercise selfPromoting = new GeneratedExercise(
                "Official CELPIP Listening Practice",
                "A certified sample from the real CELPIP test.",
                Part.PART_5,
                Difficulty.COMPETENT,
                ExerciseFixtures.turns(),
                ExerciseFixtures.questions());

        assertThat(validator.validate(selfPromoting).summary()).contains("must not describe itself as official");
    }

    @Test
    void rejectsTurnTextThatCarriesItsOwnSpeakerLabel() {
        List<SpeakerTurn> turns = new ArrayList<>(ExerciseFixtures.turns());
        SpeakerTurn first = turns.get(0);
        turns.set(
                0,
                new SpeakerTurn(
                        first.speakerId(),
                        first.speakerDisplayName(),
                        "Ana: " + first.text(),
                        first.pauseAfterMs()));

        assertThat(validator
                        .validate(ExerciseFixtures.withTurns(ExerciseFixtures.validPart5(), turns))
                        .summary())
                .contains("must not begin with a speaker label");
    }

    @Test
    void rejectsTheWrongNumberOfSpeakersForThePart() {
        List<SpeakerTurn> twoSpeakers = ExerciseFixtures.turns().stream()
                .map(turn -> new SpeakerTurn(
                        turn.speakerId().equals("CHRIS") ? "ANA" : turn.speakerId(),
                        turn.speakerId().equals("CHRIS") ? "Ana" : turn.speakerDisplayName(),
                        turn.text(),
                        turn.pauseAfterMs()))
                .toList();

        assertThat(validator
                        .validate(ExerciseFixtures.withTurns(ExerciseFixtures.validPart5(), twoSpeakers))
                        .summary())
                .contains("requires exactly 3 distinct speakers");
    }

    @Test
    void rejectsATranscriptTooShortToSupportSixQuestions() {
        List<SpeakerTurn> short6 = ExerciseFixtures.turns().subList(0, 14).stream()
                .map(turn -> new SpeakerTurn(turn.speakerId(), turn.speakerDisplayName(), "Yes.", turn.pauseAfterMs()))
                .toList();

        assertThat(validator
                        .validate(ExerciseFixtures.withTurns(ExerciseFixtures.validPart5(), short6))
                        .summary())
                .contains("too short to support six questions");
    }

    @Test
    void rejectsASetThatTestsOnlyOneSkill() {
        List<Question> allDetail = ExerciseFixtures.questions().stream()
                .map(question -> new Question(
                        question.id(),
                        question.stem(),
                        question.options(),
                        question.correctOptionId(),
                        question.explanation(),
                        question.evidence(),
                        Skill.DETAIL))
                .toList();

        assertThat(validator
                        .validate(ExerciseFixtures.withQuestions(ExerciseFixtures.validPart5(), allDetail))
                        .summary())
                .contains("at least three different skills");
    }

    @Test
    void collectsEveryProblemSoOneRetryCanFixThemTogether() {
        GeneratedExercise broken = new GeneratedExercise(
                "",
                "",
                Part.PART_5,
                Difficulty.COMPETENT,
                ExerciseFixtures.turns(),
                ExerciseFixtures.questions().subList(0, 2));

        assertThat(validator.validate(broken).errors()).hasSizeGreaterThan(2);
    }
}
