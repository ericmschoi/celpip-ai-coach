package com.listenspeak.coach.listening.domain;

import com.listenspeak.coach.platform.web.ApiException;
import java.util.Arrays;

/**
 * Style profiles for the six listening parts. These describe the shape of an
 * original exercise the generator should write; no official test content is
 * referenced or reproduced.
 */
public enum Part {
    PART_1(
            1,
            "Problem Solving",
            "Two people discuss a practical problem, weigh alternatives and constraints, and reach a decision.",
            2,
            10,
            16),
    PART_2(
            2,
            "Daily Life Conversation",
            "Two people talk through plans, responsibilities, schedules, or a service interaction.",
            2,
            10,
            16),
    PART_3(
            3,
            "Information",
            "One main speaker explains a process, place, program, or set of instructions, "
                    + "with a short clarifying question or two.",
            2,
            8,
            14),
    PART_4(
            4,
            "News Item",
            "A short broadcast report: what happened, why, who reacted, and what it implies.",
            1,
            6,
            10),
    PART_5(
            5,
            "Discussion",
            "Three speakers hold distinct positions, respond to one another, compromise, "
                    + "and reveal attitude or implication.",
            3,
            14,
            22),
    PART_6(
            6,
            "Viewpoints",
            "A speaker or interviewee presents an opinion with supporting reasons, contrasts, "
                    + "and an inferable stance.",
            2,
            8,
            14);

    private final int number;
    private final String label;
    private final String profile;
    private final int speakerCount;
    private final int minTurns;
    private final int maxTurns;

    Part(int number, String label, String profile, int speakerCount, int minTurns, int maxTurns) {
        this.number = number;
        this.label = label;
        this.profile = profile;
        this.speakerCount = speakerCount;
        this.minTurns = minTurns;
        this.maxTurns = maxTurns;
    }

    public int number() {
        return number;
    }

    public String label() {
        return label;
    }

    /** Authoring guidance handed to the generator. */
    public String profile() {
        return profile;
    }

    public int speakerCount() {
        return speakerCount;
    }

    public int minTurns() {
        return minTurns;
    }

    public int maxTurns() {
        return maxTurns;
    }

    public static Part ofNumber(int number) {
        return Arrays.stream(values())
                .filter(part -> part.number == number)
                .findFirst()
                .orElseThrow(() -> ApiException.validation("Listening part must be between 1 and 6."));
    }
}
