package com.listenspeak.coach.platform.web;

/**
 * The only exception type controllers and services throw for expected failures.
 * The message is client-safe: it must never contain a secret, a provider
 * response body, or another user's data.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ApiException(ErrorCode code, String detail, Throwable cause) {
        super(detail, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " was not found.");
    }

    public static ApiException forbidden() {
        // Deliberately vague: a cross-user probe must not learn whether the
        // resource exists.
        return new ApiException(ErrorCode.NOT_FOUND, "The requested resource was not found.");
    }

    public static ApiException validation(String detail) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, detail);
    }
}
