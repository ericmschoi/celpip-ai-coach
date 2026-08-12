package com.listenspeak.coach.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every failure into an RFC 9457 Problem Details response carrying a
 * stable {@code code} and a {@code retryable} hint. Unexpected exceptions are
 * logged with their stack trace but reported to the client generically, so a
 * provider message or an internal path can never leak through the API.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException exception, HttpServletRequest request) {
        ErrorCode code = exception.code();
        if (code.status().is5xxServerError()) {
            log.error("API failure code={} path={}", code, request.getRequestURI(), exception);
        } else {
            log.info("API rejection code={} path={} detail={}", code, request.getRequestURI(), exception.getMessage());
        }
        return ResponseEntity.status(code.status()).body(problem(code, exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(problem(ErrorCode.UNAUTHORIZED, "Sign in to continue.", request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(problem(ErrorCode.FORBIDDEN, "You do not have access to this resource.", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception path={}", request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem(
                        ErrorCode.INTERNAL_ERROR, "Something went wrong. Please try again.", request.getRequestURI()));
    }

    /** Bean-validation failures additionally carry a field-level error list. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail detail =
                problem(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.", instanceOf(request));

        List<Map<String, String>> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
                .toList();
        detail.setProperty("errors", fieldErrors);

        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).body(detail);
    }

    /**
     * Every exception Spring MVC handles itself (unsupported media type, upload
     * too large, unknown route, unreadable body) still comes back in the same
     * envelope, so the client only ever branches on {@code code}.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail detail) {
            decorate(detail, codeForStatus(statusCode), instanceOf(request));
        }
        return response;
    }

    private static ErrorCode codeForStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> ErrorCode.NOT_FOUND;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 429 -> ErrorCode.RATE_LIMITED;
            default -> status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.VALIDATION_FAILED;
        };
    }

    private static String instanceOf(WebRequest request) {
        return request.getDescription(false).replaceFirst("^uri=", "");
    }

    private static ProblemDetail problem(ErrorCode code, String detail, String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        decorate(problem, code, instance);
        return problem;
    }

    private static void decorate(ProblemDetail problem, ErrorCode code, String instance) {
        problem.setType(code.type());
        problem.setTitle(code.title());
        problem.setInstance(URI.create(instance));
        problem.setProperty("code", code.name());
        problem.setProperty("retryable", code.retryable());
    }
}
