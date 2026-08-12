package com.listenspeak.coach.listening;

import com.listenspeak.coach.listening.domain.ListeningAttempt;
import com.listenspeak.coach.listening.domain.ListeningExercise;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory repository for local development, tests, and the demo path.
 * Expired exercises are filtered on read, mirroring how DynamoDB TTL behaves
 * (deletion is eventual, so a reader must not trust that an expired item is
 * already gone).
 */
@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "LOCAL", matchIfMissing = true)
public class InMemoryListeningExerciseRepository implements ListeningExerciseRepository {

    private final Map<String, ListeningExercise> exercises = new ConcurrentHashMap<>();
    private final Map<String, ListeningAttempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryListeningExerciseRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(ListeningExercise exercise) {
        exercises.put(compositeKey(exercise.ownerId(), exercise.id()), exercise);
    }

    @Override
    public Optional<ListeningExercise> findByOwnerAndId(String ownerId, UUID exerciseId) {
        return Optional.ofNullable(exercises.get(compositeKey(ownerId, exerciseId)))
                .filter(exercise -> exercise.expiresAt() == null
                        || exercise.expiresAt().isAfter(clock.instant()));
    }

    @Override
    public void saveAttempt(ListeningAttempt attempt) {
        attempts.put(compositeKey(attempt.ownerId(), attempt.exerciseId()), attempt);
    }

    @Override
    public Optional<ListeningAttempt> findAttemptByOwnerAndExercise(String ownerId, UUID exerciseId) {
        return Optional.ofNullable(attempts.get(compositeKey(ownerId, exerciseId)));
    }

    @Override
    public List<ListeningAttempt> findRecentAttempts(String ownerId, int limit) {
        return attempts.values().stream()
                .filter(attempt -> attempt.ownerId().equals(ownerId))
                .sorted(Comparator.comparing(ListeningAttempt::submittedAt).reversed())
                .limit(limit)
                .toList();
    }

    private static String compositeKey(String ownerId, UUID exerciseId) {
        return ownerId + "/" + exerciseId;
    }
}
