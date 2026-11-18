package com.crispprep.reviews.dto;

import jakarta.validation.constraints.NotBlank;

/** The ID token ("credential") that Google's sign-in button gives the web page. */
public record GoogleLoginRequest(@NotBlank String idToken) { }
