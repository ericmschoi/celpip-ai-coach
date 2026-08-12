package com.listenspeak.coach.listening.domain;

import com.listenspeak.coach.listening.Difficulty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A persisted exercise, owned by exactly one user.
 *
 * <p>This is the server-side model. It holds the full transcript and the answer
 * key; the type returned to the browser before submission is a different type
 * that has no such fields.
 *
 * @param audioKey storage key for the assembled audio, never a public URL
 * @param sourceRef which seed fixture or model produced it, for support and debugging
 */
public record ListeningExercise(
        UUID id,
        String ownerId,
        Part part,
        Difficulty difficulty,
        String title,
        String scenario,
        List<SpeakerTurn> speakerTurns,
        List<Question> questions,
        String audioKey,
        int audioDurationSeconds,
        String sourceRef,
        String generalTip,
        Instant createdAt,
        Instant expiresAt) {

    public ListeningExercise {
        speakerTurns = List.copyOf(speakerTurns);
        questions = List.copyOf(questions);
    }

    public static ListeningExercise from(
            GeneratedExercise generated,
            String ownerId,
            String audioKey,
            int audioDurationSeconds,
            String sourceRef,
            String generalTip,
            Instant createdAt,
            Instant expiresAt) {

        return new ListeningExercise(
                UUID.randomUUID(),
                ownerId,
                generated.part(),
                generated.difficulty(),
                generated.title(),
                generated.scenario(),
                generated.speakerTurns(),
                generated.questions(),
                audioKey,
                audioDurationSeconds,
                sourceRef,
                generalTip,
                createdAt,
                expiresAt);
    }

    public Question question(String questionId) {
        return questions.stream()
                .filter(question -> question.id().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown question " + questionId));
    }

    public boolean isOwnedBy(String userId) {
        return ownerId.equals(userId);
    }
}
