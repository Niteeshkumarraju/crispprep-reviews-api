package com.crispprep.reviews.dto;

public record AuthResponse(String token, long expiresInSeconds, Long userId, String name, String email, String role) { }
