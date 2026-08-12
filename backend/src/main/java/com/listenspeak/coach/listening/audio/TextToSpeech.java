package com.listenspeak.coach.listening.audio;

/**
 * Renders one line of dialogue as clean WAV audio.
 *
 * <p>Implementations must not speak a speaker label: the text handed in is
 * exactly what should be heard.
 */
public interface TextToSpeech {

    /**
     * @param text the spoken line, with no speaker label
     * @param voice a voice id from {@link VoiceAssignment#pool()}
     * @return WAV bytes
     */
    byte[] synthesize(String text, String voice);
}
