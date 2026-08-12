package com.listenspeak.coach.listening.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The client sends only question ids and chosen option ids. Nothing here can
 * influence the score beyond which option was picked.
 */
public record SubmissionRequest(@NotEmpty @Size(max = 20) @Valid List<Answer> answers) {

    public record Answer(
            @NotNull @Pattern(regexp = "q[1-9][0-9]?", message = "must be a question id such as q1") String questionId,
            @NotNull @Pattern(regexp = "[A-D]", message = "must be A, B, C, or D") String selectedOptionId) {}
}
