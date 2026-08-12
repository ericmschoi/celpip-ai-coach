package com.listenspeak.coach.speaking.evaluation;

import java.nio.file.Path;

/**
 * Turns a completed recording into text.
 *
 * <p><strong>An implementation must only ever return words that were actually
 * spoken in the supplied recording.</strong> Returning placeholder or sample
 * text would put words in the user's mouth, which the UI then shows back to
 * them as "what we heard". If an implementation cannot hear, it says so through
 * {@link #producesRealTranscript()} and returns an empty string.
 */
public interface Transcriber {

    /**
     * @param recording a local temp file, always an application-generated path
     * @param filename the name to send with the multipart upload; must carry a
     *     correct extension because the provider uses it to detect the format
     * @return exactly what was said, or an empty string if nothing was said
     */
    String transcribe(Path recording, String filename);

    /**
     * False when this implementation cannot transcribe at all, so callers know
     * that an empty result means "no transcription available" rather than "the
     * user said nothing". Nothing downstream may present content as the user's
     * words when this is false.
     */
    default boolean producesRealTranscript() {
        return true;
    }
}
