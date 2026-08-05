package com.finpilot.auth.utils;

import com.finpilot.auth.exceptions.UnauthorizedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

// Reads the logged-in user's ID that JwtAuthFilter put into Spring
// Security's context, if any.
public class CurrentUser {

    // Strict version — throws if nobody's logged in. Use this once an
    // endpoint fully requires auth and has no fallback.
    public static UUID requireUserId() {
        return optionalUserId()
                .orElseThrow(() -> new UnauthorizedException("You must be logged in to do this"));
    }

    // Soft version — returns empty instead of throwing. Use this where a
    // fallback (like a manual userId param) is still allowed.
    public static Optional<UUID> optionalUserId() {
        var authentication = SecurityContextHolder.
                getContext().getAuthentication();
        Object principal = authentication != null ?
                authentication.getPrincipal() : null;

        if (principal instanceof UUID userId) {
            return Optional.of(userId);
        }
        return Optional.empty();
    }
}