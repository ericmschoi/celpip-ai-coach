package com.listenspeak.coach.listening;

import com.listenspeak.coach.listening.domain.ListeningAttempt;
import com.listenspeak.coach.listening.domain.ListeningExercise;
import com.listenspeak.coach.platform.aws.SingleTable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * DynamoDB single-table repository.
 *
 * <p>Sort keys:
 *
 * <pre>
 *   EXERCISE#{exerciseId}                     TTL: yes
 *   LISTENING_ATTEMPT#{exerciseId}            TTL: no
 *   LISTENING_HISTORY#{createdAt}#{attemptId} TTL: no
 * </pre>
 *
 * The attempt is written twice: once keyed by exercise, so "was this already
 * submitted" is a single get, and once keyed by time, so the history list is a
 * single reverse query. Duplicating a small item is cheaper than a GSI.
 */
@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "AWS")
public class DynamoListeningExerciseRepository implements ListeningExerciseRepository {

    private final SingleTable table;

    public DynamoListeningExerciseRepository(SingleTable table) {
        this.table = table;
    }

    @Override
    public void save(ListeningExercise exercise) {
        table.put(exercise.ownerId(), exerciseKey(exercise.id()), exercise, exercise.expiresAt());
    }

    @Override
    public Optional<ListeningExercise> findByOwnerAndId(String ownerId, UUID exerciseId) {
        return table.get(ownerId, exerciseKey(exerciseId), ListeningExercise.class);
    }

    @Override
    public void saveAttempt(ListeningAttempt attempt) {
        table.put(attempt.ownerId(), attemptKey(attempt.exerciseId()), attempt, null);
        table.put(
                attempt.ownerId(),
                "LISTENING_HISTORY#%s#%s".formatted(attempt.submittedAt(), attempt.id()),
                attempt,
                null);
    }

    @Override
    public Optional<ListeningAttempt> findAttemptByOwnerAndExercise(String ownerId, UUID exerciseId) {
        return table.get(ownerId, attemptKey(exerciseId), ListeningAttempt.class);
    }

    @Override
    public List<ListeningAttempt> findRecentAttempts(String ownerId, int limit) {
        return table.queryByPrefix(ownerId, "LISTENING_HISTORY#", limit, ListeningAttempt.class);
    }

    private static String exerciseKey(UUID exerciseId) {
        return "EXERCISE#" + exerciseId;
    }

    private static String attemptKey(UUID exerciseId) {
        return "LISTENING_ATTEMPT#" + exerciseId;
    }
}
