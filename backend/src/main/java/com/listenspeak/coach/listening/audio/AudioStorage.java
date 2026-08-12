package com.listenspeak.coach.listening.audio;

import java.time.Duration;
import java.util.Optional;

/**
 * Where assembled audio lives. Implementations must never return a URL that is
 * valid indefinitely, and must never make an object publicly readable.
 */
public interface AudioStorage {

    /** Stores the object, or does nothing if that key already holds content. */
    void storeIfAbsent(String key, byte[] content, String contentType);

    void store(String key, byte[] content, String contentType);

    /**
     * A short-lived URL the browser can put straight into an {@code <audio>}
     * element. The URL itself is the credential, so the TTL must stay short.
     */
    String presignedUrl(String key, Duration ttl);

    Optional<byte[]> read(String key);

    void delete(String key);
}
