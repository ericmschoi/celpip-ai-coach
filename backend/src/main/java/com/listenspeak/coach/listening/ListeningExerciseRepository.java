package com.listenspeak.coach.listening;

import com.listenspeak.coach.listening.domain.ListeningAttempt;
import com.listenspeak.coach.listening.domain.ListeningExercise;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port.
 *
 * <p>Every method takes the owner id first, and there is deliberately no
 * "find by id" that omits it. A cross-user read is therefore not expressible,
 * rather than being prevented by a check somebody could forget to write.
 */
public interface ListeningExerciseRepository {

    void save(ListeningExercise exercise);

    Optional<ListeningExercise> findByOwnerAndId(String ownerId, UUID exerciseId);

    void saveAttempt(ListeningAttempt attempt);

    Optional<ListeningAttempt> findAttemptByOwnerAndExercise(String ownerId, UUID exerciseId);

    List<ListeningAttempt> findRecentAttempts(String ownerId, int limit);
}
