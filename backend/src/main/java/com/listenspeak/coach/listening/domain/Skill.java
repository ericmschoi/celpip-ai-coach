package com.listenspeak.coach.listening.domain;

/**
 * What a question is actually testing. A generated set must cover a useful mix
 * rather than six literal-detail questions, and the post-submission tip is
 * chosen from the skill the user missed most.
 */
public enum Skill {
    DETAIL("Track concrete facts: numbers, dates, names, and conditions."),
    PURPOSE("Ask why a speaker says something, not only what they say."),
    SPEAKER_IDENTIFICATION("Hold each speaker's position separately as the discussion moves."),
    PARAPHRASE("Expect the correct option to restate the idea in different words."),
    INFERENCE("Combine two statements to reach a conclusion nobody says outright."),
    ATTITUDE("Listen for hedging, stress, and word choice that reveal how a speaker feels."),
    FINAL_POSITION("Notice where a speaker changes their mind and what they settle on.");

    private final String tip;

    Skill(String tip) {
        this.tip = tip;
    }

    /** One targeted, actionable listening tip for this skill. */
    public String tip() {
        return tip;
    }
}
