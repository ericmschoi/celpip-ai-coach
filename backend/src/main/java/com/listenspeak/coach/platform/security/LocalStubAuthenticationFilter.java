package com.listenspeak.coach.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Development-only authentication. It trusts the {@code X-Dev-User} header, so
 * it is registered only when {@code app.auth.mode=LOCAL_STUB} and must never be
 * enabled in a deployed environment. See docs/security.md.
 */
public class LocalStubAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Dev-User";
    public static final String DEFAULT_USER_ID = "local-dev-user";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String header = request.getHeader(HEADER);
            String userId = (header == null || header.isBlank()) ? DEFAULT_USER_ID : header.trim();
            AuthenticatedUser user = new AuthenticatedUser(userId);

            var authentication = new UsernamePasswordAuthenticationToken(
                    user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        chain.doFilter(request, response);
    }
}
