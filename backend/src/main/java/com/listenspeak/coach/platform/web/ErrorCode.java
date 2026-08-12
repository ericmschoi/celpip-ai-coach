package com.listenspeak.coach.platform.web;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Stable, client-facing error codes. The frontend branches on {@link #name()},
 * never on a message string, so wording can change without breaking the UI.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request is not valid", false),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found", false),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Not allowed", false),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required", false),
    ALREADY_SUBMITTED(HttpStatus.CONFLICT, "Already submitted", false),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported audio format", false),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Recording is too large", false),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests", true),
    DAILY_LIMIT_REACHED(HttpStatus.TOO_MANY_REQUESTS, "Daily practice limit reached", false),

    /** The provider refused to answer for content-policy reasons. */
    PROVIDER_REFUSED(HttpStatus.UNPROCESSABLE_ENTITY, "The AI provider declined this request", false),
    /** The provider answered, but not in a shape that passes validation. */
    GENERATION_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Generated exercise failed validation", true),
    PROVIDER_RATE_LIMITED(HttpStatus.SERVICE_UNAVAILABLE, "The AI provider is rate limiting us", true),
    PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The AI provider is unavailable", true),
    PROVIDER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "The AI provider took too long", true),
    PROVIDER_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "AI features are not configured", false),
    AUDIO_QUALITY_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "Generated audio failed quality checks", true),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong", true);

    private static final String TYPE_BASE = "https://listenspeak.app/problems/";

    private final HttpStatus status;
    private final String title;
    private final boolean retryable;

    ErrorCode(HttpStatus status, String title, boolean retryable) {
        this.status = status;
        this.title = title;
        this.retryable = retryable;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** True when repeating the identical request could plausibly succeed. */
    public boolean retryable() {
        return retryable;
    }

    public URI type() {
        return URI.create(TYPE_BASE + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
