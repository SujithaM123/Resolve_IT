package com.dtcc.intern.demo.dto;

public record LoginResponse(
        String token,
        Long userId,
        String name,
        String role) {
}
