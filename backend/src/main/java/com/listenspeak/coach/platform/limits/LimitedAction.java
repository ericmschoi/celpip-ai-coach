package com.listenspeak.coach.platform.limits;

/** The operations that cost money and therefore need a per-user ceiling. */
public enum LimitedAction {
    LISTENING_GENERATION("listening exercise"),
    SPEAKING_EVALUATION("speaking evaluation");

    private final String label;

    LimitedAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
