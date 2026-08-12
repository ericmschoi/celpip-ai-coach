package com.listenspeak.coach.listening.generation;

import com.listenspeak.coach.listening.Difficulty;
import com.listenspeak.coach.listening.domain.GeneratedExercise;
import com.listenspeak.coach.listening.domain.Part;
import com.listenspeak.coach.platform.config.AppProperties.ContentMode;

/**
 * Produces an exercise plus its audio. Implementations are the deterministic
 * seed library and the OpenAI pipeline; the service above them cannot tell
 * which one it is talking to.
 */
public interface ExerciseGenerator {

    /**
     * @param audioKey storage key of the assembled audio, already stored
     * @param durationSeconds measured duration of that audio
     * @param sourceRef seed id or model name, recorded for support
     * @param tip a general listening tip, shown only when the user scores full marks
     */
    record Generated(
            GeneratedExercise exercise, String audioKey, int durationSeconds, String sourceRef, String tip) {}

    /** Which content mode this generator serves. */
    ContentMode mode();

    Generated generate(Part part, Difficulty difficulty);
}
