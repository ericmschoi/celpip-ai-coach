package com.listenspeak.coach.platform.media;

/** A local media operation failed. Never carries provider or user content in its message. */
public class MediaProcessingException extends RuntimeException {

    public MediaProcessingException(String message) {
        super(message);
    }

    public MediaProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
