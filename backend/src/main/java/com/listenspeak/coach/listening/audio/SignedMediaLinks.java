package com.listenspeak.coach.listening.audio;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * The local-mode equivalent of an S3 presigned URL: an expiring, signed link
 * that an {@code <audio>} element can fetch without an Authorization header,
 * which media elements cannot send.
 *
 * <p>The signing key is random per process, so every restart invalidates old
 * links. That is the desired behaviour for a development-only mechanism.
 */
@Component
public class SignedMediaLinks {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] signingKey = new byte[32];
    private final Clock clock;

    public SignedMediaLinks(Clock clock) {
        this.clock = clock;
        new SecureRandom().nextBytes(signingKey);
    }

    public String sign(String key, Duration ttl) {
        long expiresAt = clock.instant().plus(ttl).getEpochSecond();
        return "%s:%d:%s".formatted(encode(key), expiresAt, mac(key, expiresAt));
    }

    /** Returns the storage key when the token is well-formed, unexpired, and correctly signed. */
    public String verify(String token) {
        String[] parts = token.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed media token");
        }

        String key = decode(parts[0]);
        long expiresAt;
        try {
            expiresAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformed media token", e);
        }

        if (clock.instant().getEpochSecond() > expiresAt) {
            throw new IllegalArgumentException("Media link has expired");
        }
        if (!constantTimeEquals(mac(key, expiresAt), parts[2])) {
            throw new IllegalArgumentException("Media link signature does not match");
        }
        return key;
    }

    private String mac(String key, long expiresAt) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, ALGORITHM));
            byte[] digest = mac.doFinal("%s|%d".formatted(key, expiresAt).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign media link", e);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
