package com.listenspeak.coach.speaking.evaluation;

/**
 * Decides whether a recording contains any speech worth evaluating.
 *
 * <p>This runs before anything is transcribed or scored. Without it, a user who
 * pressed stop immediately, or whose microphone was muted, would be handed an
 * evaluation of nothing — and in demo mode, where there is no transcription at
 * all, that evaluation would look indistinguishable from one of real speech.
 */
public final class SpeechPresence {

    /** Below this much audible time there is nothing to assess. */
    static final double MIN_SPEECH_SECONDS = 1.5;

    /** At or above this proportion of silence, the recording is empty in practice. */
    static final double MAX_SILENCE_RATIO = 0.95;

    private SpeechPresence() {}

    public static boolean hasSpeech(RecordingAnalyzer.Measurements measurements) {
        if (measurements.silenceRatio() >= MAX_SILENCE_RATIO) {
            return false;
        }
        double audibleSeconds = measurements.durationSeconds() * (1 - measurements.silenceRatio());
        return audibleSeconds >= MIN_SPEECH_SECONDS;
    }
}
