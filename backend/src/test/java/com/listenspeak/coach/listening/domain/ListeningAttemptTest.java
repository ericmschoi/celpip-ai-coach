package com.listenspeak.coach.listening.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.listenspeak.coach.listening.Difficulty;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ListeningAttemptTest {

    private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");

    private ListeningExercise exercise() {
        GeneratedExercise generated = ExerciseFixtures.validPart5();
        return ListeningExercise.from(
                generated, "owner", "listening/test.mp3", 180, "test", "general tip", NOW, NOW.plusSeconds(3600));
    }

    private Map<String, String> selections(String... optionIds) {
        Map<String, String> selections = new LinkedHashMap<>();
        for (int i = 0; i < optionIds.length; i++) {
            selections.put("q" + (i + 1), optionIds[i]);
        }
        return selections;
    }

    @Test
    void scoresOnlyFromThePersistedAnswerKey() {
        ListeningAttempt attempt =
                ListeningAttempt.score(exercise(), "owner", selections("B", "B", "B", "B", "B", "B"), NOW);

        assertThat(attempt.correctCount()).isEqualTo(6);
        assertThat(attempt.scorePercent()).isEqualTo(100);
        assertThat(attempt.weakestSkill()).isNull();
    }

    @Test
    void marksEveryWrongAnswerAndReportsAWholeNumberPercentage() {
        ListeningAttempt attempt =
                ListeningAttempt.score(exercise(), "owner", selections("B", "A", "B", "A", "B", "A"), NOW);

        assertThat(attempt.correctCount()).isEqualTo(3);
        assertThat(attempt.scorePercent()).isEqualTo(50);
        assertThat(attempt.results()).extracting(ListeningAttempt.QuestionResult::correct)
                .containsExactly(true, false, true, false, true, false);
    }

    @Test
    void isDeterministicForTheSameAnswers() {
        Map<String, String> answers = selections("A", "A", "B", "B", "C", "D");

        ListeningAttempt first = ListeningAttempt.score(exercise(), "owner", answers, NOW);
        ListeningAttempt second = ListeningAttempt.score(exercise(), "owner", answers, NOW);

        assertThat(second.correctCount()).isEqualTo(first.correctCount());
        assertThat(second.weakestSkill()).isEqualTo(first.weakestSkill());
        assertThat(second.results()).usingRecursiveComparison().isEqualTo(first.results());
    }

    @Test
    void picksTheSkillWithTheMostMissesForTheTip() {
        // q2 is PURPOSE, q3 SPEAKER_IDENTIFICATION, q5 INFERENCE, q6 FINAL_POSITION.
        // Missing both DETAIL questions (q1 and q4) makes DETAIL the weakest.
        ListeningAttempt attempt =
                ListeningAttempt.score(exercise(), "owner", selections("A", "B", "B", "A", "B", "B"), NOW);

        assertThat(attempt.weakestSkill()).isEqualTo(Skill.DETAIL);
        assertThat(attempt.weakestSkill().tip()).isNotBlank();
    }

    @Test
    void treatsAnUnansweredQuestionAsWrongRatherThanFailing() {
        Map<String, String> partial = selections("B", "B", "B", "B", "B");

        ListeningAttempt attempt = ListeningAttempt.score(exercise(), "owner", partial, NOW);

        assertThat(attempt.correctCount()).isEqualTo(5);
        assertThat(attempt.results().get(5).selectedOptionId()).isNull();
        assertThat(attempt.results().get(5).correct()).isFalse();
    }
}
