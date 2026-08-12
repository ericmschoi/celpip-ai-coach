package com.listenspeak.coach.listening.api;

import com.listenspeak.coach.listening.Difficulty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateExerciseRequest(
        @Min(value = 1, message = "must be between 1 and 6") @Max(value = 6, message = "must be between 1 and 6")
                int part,
        @NotNull(message = "must be DEVELOPING, COMPETENT, or ADVANCED") Difficulty difficulty) {}
