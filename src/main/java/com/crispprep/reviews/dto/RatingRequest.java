package com.crispprep.reviews.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RatingRequest(
        @NotBlank @Size(max = 50) String userName,
        @Min(1) @Max(5) int stars) { }