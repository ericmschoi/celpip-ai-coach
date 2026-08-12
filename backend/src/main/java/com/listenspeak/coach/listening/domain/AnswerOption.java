package com.listenspeak.coach.listening.domain;

/**
 * One of the four choices. {@code id} is always A, B, C, or D.
 */
public record AnswerOption(String id, String text) {

    public AnswerOption {
        if (id == null || !id.matches("[A-D]")) {
            throw new IllegalArgumentException("Option id must be A, B, C, or D");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Option text must not be blank");
        }
    }
}
