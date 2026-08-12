package com.listenspeak.coach.listening.domain;

import java.util.List;

/**
 * A single four-option question together with everything needed to explain it
 * after submission. {@code correctOptionId}, {@code explanation}, and
 * {@code evidence} exist only on the domain model - never on the type returned
 * before the user submits.
 *
 * @param evidence short paraphrase or excerpt from the dialogue that supports the answer
 */
public record Question(
        String id,
        String stem,
        List<AnswerOption> options,
        String correctOptionId,
        String explanation,
        String evidence,
        Skill skill) {

    public Question {
        options = List.copyOf(options);
    }

    public AnswerOption correctOption() {
        return options.stream()
                .filter(option -> option.id().equals(correctOptionId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Question " + id + " has no option " + correctOptionId));
    }

    public boolean isCorrect(String selectedOptionId) {
        return correctOptionId.equals(selectedOptionId);
    }
}
