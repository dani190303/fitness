package com.fitness.auth.dto;

import org.springframework.security.core.Authentication;

public record AuthResult(
        UserResponse user,
        Authentication authentication
) {
}
