package com.crispprep.reviews.dto;

import java.time.Instant;

public record ReviewView(Long id, String userName, String text, Instant createdAt) { }