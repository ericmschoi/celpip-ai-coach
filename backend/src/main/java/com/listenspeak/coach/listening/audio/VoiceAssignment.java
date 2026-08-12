package com.listenspeak.coach.listening.audio;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;

/**
 * Maps speaker ids to TTS voices.
 *
 * <p>Assignment is by first appearance in the dialogue, so the same exercise
 * always produces the same voices. Speakers within one exercise must sound
 * clearly different or the "who said it" questions become guesswork.
 */
public final class VoiceAssignment {

    /**
     * Ordered by how distinguishable the first three are from one another,
     * because Part 5 needs exactly three clearly separable voices.
     */
    private static final List<String> VOICE_POOL = List.of("marin", "cedar", "sage", "verse", "coral", "ash");

    private VoiceAssignment() {}

    public static Map<String, String> assign(SequencedSet<String> speakerIds) {
        if (speakerIds.size() > VOICE_POOL.size()) {
            throw new IllegalArgumentException(
                    "No distinct voice available for %d speakers".formatted(speakerIds.size()));
        }

        Map<String, String> voices = new LinkedHashMap<>();
        int index = 0;
        for (String speakerId : speakerIds) {
            voices.put(speakerId, VOICE_POOL.get(index++));
        }
        // Insertion order is preserved deliberately: it is the assignment rule,
        // and it makes the logged mapping readable.
        return java.util.Collections.unmodifiableMap(voices);
    }

    public static List<String> pool() {
        return VOICE_POOL;
    }
}
