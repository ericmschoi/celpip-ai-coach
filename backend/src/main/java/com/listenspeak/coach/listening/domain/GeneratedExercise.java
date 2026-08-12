package com.listenspeak.coach.listening.domain;

import com.listenspeak.coach.listening.Difficulty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.SequencedSet;

/**
 * What a generator produces, before it is persisted or given an owner or audio.
 * Keeping this separate from {@link ListeningExercise} means a generator cannot
 * accidentally fabricate an id, an owner, or an audio reference.
 */
public record GeneratedExercise(
        String title,
        String scenario,
        Part part,
        Difficulty difficulty,
        List<SpeakerTurn> speakerTurns,
        List<Question> questions) {

    public GeneratedExercise {
        speakerTurns = List.copyOf(speakerTurns);
        questions = List.copyOf(questions);
    }

    /** Distinct speaker ids in first-appearance order, which is how voices are assigned. */
    public SequencedSet<String> speakerIds() {
        SequencedMap<String, Boolean> ordered = new LinkedHashMap<>();
        speakerTurns.forEach(turn -> ordered.put(turn.speakerId(), Boolean.TRUE));
        return ordered.sequencedKeySet();
    }

    public String transcriptText() {
        return speakerTurns.stream().map(SpeakerTurn::text).reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
    }

    public int transcriptWordCount() {
        return speakerTurns.stream().mapToInt(SpeakerTurn::wordCount).sum();
    }
}
