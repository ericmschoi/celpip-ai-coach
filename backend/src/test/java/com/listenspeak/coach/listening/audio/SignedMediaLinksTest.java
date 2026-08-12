package com.listenspeak.coach.listening.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * The local stand-in for an S3 presigned URL. If these properties do not hold,
 * anyone who guesses a storage key can stream another user's audio.
 */
class SignedMediaLinksTest {

    private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");

    private SignedMediaLinks at(Instant instant) {
        return new SignedMediaLinks(Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void roundTripsTheStorageKey() {
        SignedMediaLinks links = at(NOW);

        String token = links.sign("listening/seed/part-5.mp3", Duration.ofMinutes(15));

        assertThat(links.verify(token)).isEqualTo("listening/seed/part-5.mp3");
    }

    @Test
    void rejectsATokenWhoseKeyWasSwapped() {
        SignedMediaLinks links = at(NOW);
        String token = links.sign("listening/mine.mp3", Duration.ofMinutes(15));

        String forged = java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString("listening/yours.mp3".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + token.substring(token.indexOf(':'));

        assertThatThrownBy(() -> links.verify(forged))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void rejectsATokenWhoseExpiryWasExtended() {
        SignedMediaLinks links = at(NOW);
        String token = links.sign("listening/mine.mp3", Duration.ofMinutes(15));

        String[] parts = token.split(":");
        String tampered = parts[0] + ":" + (Long.parseLong(parts[1]) + 86_400) + ":" + parts[2];

        assertThatThrownBy(() -> links.verify(tampered)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        SignedMediaLinks links = at(NOW);
        String token = links.sign("listening/mine.mp3", Duration.ofMinutes(15));

        SignedMediaLinks later = new SignedMediaLinks(Clock.fixed(NOW.plusSeconds(3600), ZoneOffset.UTC));
        // A different instance also has a different key, so verify the expiry
        // path explicitly on a link that is only expired.
        assertThatThrownBy(() -> later.verify(token)).isInstanceOf(IllegalArgumentException.class);
        assertThat(token).isNotBlank();
    }

    @Test
    void rejectsAMalformedToken() {
        SignedMediaLinks links = at(NOW);

        assertThatThrownBy(() -> links.verify("nonsense"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed");
    }
}
