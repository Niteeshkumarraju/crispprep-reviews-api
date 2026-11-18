package com.crispprep.reviews.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @NotBlank @Size(max = 50) String userName,
        @NotBlank @Size(min = 10, max = 500) String text,
        @AssertTrue(message = "Consent is required to publish a review") boolean consent) { }