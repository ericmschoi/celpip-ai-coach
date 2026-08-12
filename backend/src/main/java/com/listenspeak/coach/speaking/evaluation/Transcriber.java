package com.listenspeak.coach.speaking.evaluation;

import java.nio.file.Path;

/** Turns a completed recording into text. */
public interface Transcriber {

    /**
     * @param recording a local temp file, always an application-generated path
     * @param filename the name to send with the multipart upload; must carry a
     *     correct extension because the provider uses it to detect the format
     */
    String transcribe(Path recording, String filename);
}
