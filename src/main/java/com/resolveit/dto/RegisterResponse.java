package com.resolveit.dto;

public record RegisterResponse(
        Long userId,
        String name,
        String email,
        String role) {
}
