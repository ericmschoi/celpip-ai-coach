package com.listenspeak.coach.listening.audio;

import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves locally stored audio to an {@code <audio>} element, which cannot send
 * an Authorization header. The signed, expiring token in the query string is
 * the credential - the same contract as an S3 presigned URL.
 *
 * <p>This controller exists only in {@code LOCAL} storage mode. Deployed
 * environments serve audio straight from S3 and never expose this route.
 */
@RestController
@Hidden
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "LOCAL", matchIfMissing = true)
public class LocalMediaController {

    private final AudioStorage storage;
    private final SignedMediaLinks signedLinks;

    public LocalMediaController(AudioStorage storage, SignedMediaLinks signedLinks) {
        this.storage = storage;
        this.signedLinks = signedLinks;
    }

    @GetMapping("/media/listening")
    public ResponseEntity<Resource> audio(@RequestParam String token) {
        String key;
        try {
            key = signedLinks.verify(token);
        } catch (IllegalArgumentException e) {
            // Do not distinguish expired from forged: both are just "no".
            throw new ApiException(ErrorCode.NOT_FOUND, "That audio link is no longer valid.");
        }

        byte[] content = storage.read(key).orElseThrow(() -> ApiException.notFound("Audio"));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .contentLength(content.length)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(new ByteArrayResource(content));
    }
}
