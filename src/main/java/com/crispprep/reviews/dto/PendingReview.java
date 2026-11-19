package com.crispprep.reviews.dto;

import java.time.Instant;

public record PendingReview(Long id, Long pdfId, String pdfSlug, String pdfTitle, Long userId, String userName, String userEmail,
                            String text, Instant createdAt) { }
