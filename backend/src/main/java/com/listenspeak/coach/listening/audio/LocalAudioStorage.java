package com.listenspeak.coach.listening.audio;

import com.listenspeak.coach.platform.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Filesystem-backed storage for local development and tests. Keys are treated
 * as opaque path segments and are normalised against the root, so a key can
 * never escape the storage directory.
 */
@Component
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "LOCAL", matchIfMissing = true)
public class LocalAudioStorage implements AudioStorage {

    private final Path root;
    private final SignedMediaLinks signedLinks;

    public LocalAudioStorage(AppProperties properties, SignedMediaLinks signedLinks) {
        this.root = Path.of(properties.storage().localPath(), "audio").toAbsolutePath().normalize();
        this.signedLinks = signedLinks;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create local audio directory " + root, e);
        }
    }

    @Override
    public void storeIfAbsent(String key, byte[] content, String contentType) {
        if (!Files.exists(resolve(key))) {
            store(key, content, contentType);
        }
    }

    @Override
    public void store(String key, byte[] content, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write audio " + key, e);
        }
    }

    @Override
    public String presignedUrl(String key, Duration ttl) {
        String token = URLEncoder.encode(signedLinks.sign(key, ttl), StandardCharsets.UTF_8);
        return "/media/listening?token=" + token;
    }

    @Override
    public Optional<byte[]> read(String key) {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(target));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read audio " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete audio " + key, e);
        }
    }

    /** Resolves a key under the root and refuses anything that escapes it. */
    private Path resolve(String key) {
        Path candidate = root.resolve(key).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("Audio key must stay inside the storage root");
        }
        return candidate;
    }
}
