package com.crispprep.reviews.dto;

import java.time.Instant;

public record PendingReview(Long id, Long pdfId, String pdfTitle, String userName, String text, Instant createdAt) { }
