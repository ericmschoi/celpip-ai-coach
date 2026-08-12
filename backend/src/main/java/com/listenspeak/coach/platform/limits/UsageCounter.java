package com.listenspeak.coach.platform.limits;

import java.time.LocalDate;

/**
 * Persistent daily usage tally.
 *
 * <p>Separate from the in-process burst limiter because a restart must not hand
 * a user a fresh daily allowance.
 */
public interface UsageCounter {

    /** Increments and returns the new count for that user, action, and UTC day. */
    int incrementAndGet(String userId, LimitedAction action, LocalDate day);

    int current(String userId, LimitedAction action, LocalDate day);
}
