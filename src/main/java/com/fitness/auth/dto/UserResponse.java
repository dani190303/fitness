package com.fitness.auth.dto;

public record UserResponse(
        Long id,
        String username,
        String displayName
) {
}
