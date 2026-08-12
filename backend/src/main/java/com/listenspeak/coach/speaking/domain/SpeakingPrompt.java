package com.listenspeak.coach.speaking.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An original prompt for one speaking task, together with the timings the
 * recorder must use. Timings come from the central catalog, never from the
 * generator, so a model cannot shorten or lengthen the exercise.
 *
 * @param situation the scenario the speaker is responding to
 * @param instruction what they are actually asked to do
 * @param bullets optional supporting points shown alongside the prompt
 * @param sourceRef seed id or model name, recorded for support
 */
public record SpeakingPrompt(
        UUID id,
        String ownerId,
        int taskNumber,
        String taskTitle,
        String situation,
        String instruction,
        List<String> bullets,
        int preparationSeconds,
        int answerSeconds,
        String sourceRef,
        Instant createdAt,
        Instant expiresAt) {

    public SpeakingPrompt {
        bullets = List.copyOf(bullets);
    }

    public boolean isOwnedBy(String userId) {
        return ownerId.equals(userId);
    }
}
