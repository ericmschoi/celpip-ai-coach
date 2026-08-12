package com.listenspeak.coach.platform.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Single place that turns the security context into an {@link AuthenticatedUser}.
 * Controllers must never read the principal themselves, so ownership checks all
 * start from the same value.
 */
@Component
public class CurrentUser {

    public AuthenticatedUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new AuthenticationCredentialsNotFoundException("Token has no subject claim");
            }
            return new AuthenticatedUser(subject);
        }
        if (principal instanceof AuthenticatedUser user) {
            return user;
        }
        return new AuthenticatedUser(authentication.getName());
    }
}
