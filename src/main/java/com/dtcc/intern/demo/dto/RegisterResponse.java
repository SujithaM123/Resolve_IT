package com.dtcc.intern.demo.dto;

public record RegisterResponse(
        Long userId,
        String name,
        String email,
        String role) {
}
