package com.listenspeak.coach.platform.security;

/**
 * The identity every owned resource is keyed by. {@code id} is the Cognito
 * {@code sub} in AWS, or a stubbed local identifier in development.
 */
public record AuthenticatedUser(String id) {

    public AuthenticatedUser {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Authenticated user id must not be blank");
        }
    }
}
