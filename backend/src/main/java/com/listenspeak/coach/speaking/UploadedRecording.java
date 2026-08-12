package com.listenspeak.coach.speaking;

import com.listenspeak.coach.platform.config.AppProperties;
import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

/**
 * A validated recording, written to an application-generated temp path.
 *
 * <p>The client's filename is never used: it is attacker-controlled and would
 * be a path-traversal vector. The extension is derived from the declared
 * content type, which has already been checked against an allowlist.
 *
 * <p>Always used with try-with-resources so the temp file is deleted even when
 * transcription or scoring throws.
 */
public final class UploadedRecording implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(UploadedRecording.class);

    private static final Map<String, String> EXTENSIONS = Map.of(
            "audio/webm", "webm",
            "audio/ogg", "ogg",
            "audio/mp4", "mp4",
            "audio/mpeg", "mp3",
            "audio/wav", "wav",
            "audio/x-wav", "wav");

    private final Path path;
    private final String contentType;
    private final long sizeBytes;

    private UploadedRecording(Path path, String contentType, long sizeBytes) {
        this.path = path;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public Path path() {
        return path;
    }

    public String filename() {
        return path.getFileName().toString();
    }

    public String contentType() {
        return contentType;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    /** Validates the upload and stores it under a UUID name in the system temp directory. */
    public static UploadedRecording accept(MultipartFile file, AppProperties.Speaking limits) {
        if (file == null || file.isEmpty()) {
            throw ApiException.validation("No recording was uploaded.");
        }

        String contentType = baseContentType(file.getContentType());
        List<String> allowed = limits.allowedContentTypes();
        if (contentType == null || !allowed.contains(contentType)) {
            throw new ApiException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "That audio format is not supported. Allowed formats: " + String.join(", ", allowed) + ".");
        }

        if (file.getSize() > limits.maxUploadBytes()) {
            throw new ApiException(
                    ErrorCode.PAYLOAD_TOO_LARGE,
                    "That recording is %.1f MB, over the %.0f MB limit."
                            .formatted(file.getSize() / 1_048_576.0, limits.maxUploadBytes() / 1_048_576.0));
        }

        String extension = EXTENSIONS.getOrDefault(contentType, "bin");
        Path target;
        try {
            target = Files.createTempFile("listenspeak-speaking-", "-" + UUID.randomUUID() + "." + extension);
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the uploaded recording", e);
        }

        log.debug("Accepted recording contentType={} bytes={}", contentType, file.getSize());
        return new UploadedRecording(target, contentType, file.getSize());
    }

    /**
     * Browsers append codec parameters, for example
     * {@code audio/webm;codecs=opus}. Only the base type is matched.
     */
    private static String baseContentType(String rawContentType) {
        if (rawContentType == null) {
            return null;
        }
        int separator = rawContentType.indexOf(';');
        String base = separator < 0 ? rawContentType : rawContentType.substring(0, separator);
        return base.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public void close() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete temporary recording {}", path.getFileName());
        }
    }
}
