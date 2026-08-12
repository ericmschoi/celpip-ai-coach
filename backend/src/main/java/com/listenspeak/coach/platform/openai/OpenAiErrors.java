package com.listenspeak.coach.platform.openai;

import com.listenspeak.coach.platform.web.ApiException;
import com.listenspeak.coach.platform.web.ErrorCode;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import java.net.SocketTimeoutException;

/**
 * Maps provider failures onto this application's stable error codes.
 *
 * <p>Provider response bodies are never forwarded to the client: they can echo
 * request content and occasionally headers, and the client only ever needs the
 * code.
 */
public final class OpenAiErrors {

    private OpenAiErrors() {}

    public static ApiException translate(String operation, Throwable cause) {
        if (cause instanceof ApiException apiException) {
            return apiException;
        }

        if (cause instanceof OpenAIServiceException serviceException) {
            int status = serviceException.statusCode();
            ErrorCode code =
                    switch (status) {
                        case 400, 422 -> ErrorCode.GENERATION_INVALID;
                        case 401, 403 -> ErrorCode.PROVIDER_NOT_CONFIGURED;
                        case 408, 504 -> ErrorCode.PROVIDER_TIMEOUT;
                        case 429 -> ErrorCode.PROVIDER_RATE_LIMITED;
                        default -> ErrorCode.PROVIDER_UNAVAILABLE;
                    };
            return new ApiException(code, messageFor(code), serviceException);
        }

        if (cause instanceof OpenAIIoException || cause instanceof SocketTimeoutException) {
            return new ApiException(ErrorCode.PROVIDER_TIMEOUT, messageFor(ErrorCode.PROVIDER_TIMEOUT), cause);
        }

        return new ApiException(
                ErrorCode.PROVIDER_UNAVAILABLE, "The AI service could not complete " + operation + ".", cause);
    }

    /** Raised when the model declines on content-policy grounds. */
    public static ApiException refused(String detail) {
        return new ApiException(ErrorCode.PROVIDER_REFUSED, detail);
    }

    private static String messageFor(ErrorCode code) {
        return switch (code) {
            case PROVIDER_RATE_LIMITED -> "The AI service is rate limiting us. Try again shortly.";
            case PROVIDER_TIMEOUT -> "The AI service took too long to respond. Try again.";
            case PROVIDER_NOT_CONFIGURED -> "The AI service rejected our credentials. Check the configured API key.";
            case GENERATION_INVALID -> "The AI service rejected the request as invalid.";
            default -> "The AI service is unavailable. Try again shortly.";
        };
    }
}
