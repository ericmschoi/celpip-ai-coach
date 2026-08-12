package com.listenspeak.coach.listening.domain;

/**
 * One line of the dialogue.
 *
 * <p>{@code text} is what gets spoken, so it must never contain a speaker label
 * such as {@code "Elena:"} - the label would be read aloud by the TTS voice.
 *
 * @param speakerId stable id used to pick a voice, e.g. {@code ELENA}
 * @param speakerDisplayName name shown in the transcript after submission
 * @param text the spoken line, with no speaker label
 * @param pauseAfterMs silence inserted after this turn during assembly
 */
public record SpeakerTurn(String speakerId, String speakerDisplayName, String text, int pauseAfterMs) {

    public SpeakerTurn {
        if (speakerId == null || speakerId.isBlank()) {
            throw new IllegalArgumentException("speakerId must not be blank");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Speaker turn text must not be blank");
        }
        if (pauseAfterMs < 0 || pauseAfterMs > 3000) {
            throw new IllegalArgumentException("pauseAfterMs must be between 0 and 3000");
        }
    }

    public int wordCount() {
        return text.trim().split("\\s+").length;
    }
}
