package com.listenspeak.coach.listening.domain;

import com.listenspeak.coach.listening.Difficulty;
import java.util.ArrayList;
import java.util.List;

/**
 * Builders for exercises that are valid by default, so each test can break
 * exactly one rule and assert that the validator notices.
 */
public final class ExerciseFixtures {

    private ExerciseFixtures() {}

    private static final List<String> SPEAKERS = List.of("ANA", "BEN", "CHRIS");

    /** A Part 5 exercise that passes every validation rule. */
    public static GeneratedExercise validPart5() {
        return new GeneratedExercise(
                "Choosing a Meeting Time",
                "Three colleagues decide when to hold a weekly planning meeting.",
                Part.PART_5,
                Difficulty.COMPETENT,
                turns(),
                questions());
    }

    public static List<SpeakerTurn> turns() {
        List<SpeakerTurn> turns = new ArrayList<>();
        String[] lines = {
            "We need to settle the weekly meeting time before the quarter starts, and we have until Friday to decide.",
            "Tuesday morning suits the warehouse team, because their deliveries finish before ten and nobody is driving.",
            "Tuesday is exactly when I am on the road, so I would miss every second meeting and lose the thread.",
            "Then let us look at Thursday afternoon instead, which nobody has flagged as a problem so far.",
            "Thursday afternoon works for deliveries as well, although the loading bay gets noisy around three o'clock.",
            "Noise I can live with, but I cannot join from the road, so a fixed indoor slot matters more to me.",
            "What if we move it to Thursday at one o'clock, before the loading bay gets busy for the afternoon?",
            "One o'clock is fine for the warehouse, and it gives us time to write the notes up before the end of day.",
            "One o'clock also means I am back from my route, so I will actually be in the building every week.",
            "Good. Are we agreed that Thursday at one is the standing slot from the start of next quarter?",
            "Agreed, provided we finish inside forty minutes, because the afternoon shift briefing starts at two.",
            "Forty minutes is realistic if we circulate the agenda the day before rather than reading it in the room.",
            "I will own the agenda then, and I will send it out every Wednesday morning without being chased.",
            "Then I will book the room for Thursday at one, standing, and cancel the old Tuesday invitation today.",
            "Please copy the warehouse supervisor on the invitation so the shift board can be updated in advance.",
            "Will do. Thanks both, that took ten minutes instead of the three weeks it has been sitting on my list."
        };
        for (int i = 0; i < lines.length; i++) {
            String speakerId = SPEAKERS.get(i % SPEAKERS.size());
            turns.add(new SpeakerTurn(speakerId, capitalize(speakerId), lines[i], 350));
        }
        return List.copyOf(turns);
    }

    public static List<Question> questions() {
        return List.of(
                question("q1", "When must the decision be made?", "B", "we have until Friday to decide", Skill.DETAIL),
                question(
                        "q2",
                        "Why does Tuesday not work for one speaker?",
                        "B",
                        "Tuesday is exactly when I am on the road",
                        Skill.PURPOSE),
                question(
                        "q3",
                        "Who raises the noise from the loading bay?",
                        "B",
                        "the loading bay gets noisy around three",
                        Skill.SPEAKER_IDENTIFICATION),
                question(
                        "q4",
                        "What does the group agree the meeting must not exceed?",
                        "B",
                        "provided we finish inside forty minutes",
                        Skill.DETAIL),
                question(
                        "q5",
                        "Why will circulating the agenda early help?",
                        "B",
                        "circulate the agenda the day before rather than reading it in the room",
                        Skill.INFERENCE),
                question(
                        "q6",
                        "What is the final arrangement?",
                        "B",
                        "book the room for Thursday at one, standing",
                        Skill.FINAL_POSITION));
    }

    public static Question question(String id, String stem, String correct, String evidence, Skill skill) {
        return new Question(
                id,
                stem,
                List.of(
                        new AnswerOption("A", stem + " - first distractor"),
                        new AnswerOption("B", stem + " - correct choice"),
                        new AnswerOption("C", stem + " - second distractor"),
                        new AnswerOption("D", stem + " - third distractor")),
                correct,
                "Explanation for " + id,
                evidence,
                skill);
    }

    public static GeneratedExercise withQuestions(GeneratedExercise base, List<Question> questions) {
        return new GeneratedExercise(
                base.title(), base.scenario(), base.part(), base.difficulty(), base.speakerTurns(), questions);
    }

    public static GeneratedExercise withTurns(GeneratedExercise base, List<SpeakerTurn> turns) {
        return new GeneratedExercise(
                base.title(), base.scenario(), base.part(), base.difficulty(), turns, base.questions());
    }

    private static String capitalize(String value) {
        return value.charAt(0) + value.substring(1).toLowerCase(java.util.Locale.ROOT);
    }
}
