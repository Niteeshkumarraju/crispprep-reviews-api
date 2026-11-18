package com.crispprep.reviews.controller;

import com.crispprep.reviews.dto.PendingReview;
import com.crispprep.reviews.dto.ReviewRequest;
import com.crispprep.reviews.dto.ReviewView;
import com.crispprep.reviews.entity.Pdf;
import com.crispprep.reviews.entity.Review;
import com.crispprep.reviews.entity.User;
import com.crispprep.reviews.repository.PdfRepository;
import com.crispprep.reviews.repository.ReviewRepository;
import com.crispprep.reviews.service.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final PdfRepository pdfRepository;
    private final ReviewRepository reviewRepository;
    private final CurrentUser currentUser;

    public ReviewController(PdfRepository pdfRepository, ReviewRepository reviewRepository, CurrentUser currentUser) {
        this.pdfRepository = pdfRepository;
        this.reviewRepository = reviewRepository;
        this.currentUser = currentUser;
    }

    /** Write a review as the logged-in user (needs a token). One review per user per PDF, consent required. */
    @PostMapping("/pdfs/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addReview(@PathVariable Long id, @Valid @RequestBody ReviewRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        Pdf pdf = pdfRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not found"));
        User user = currentUser.from(jwt);
        if (reviewRepository.existsByPdfIdAndUserId(id, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already reviewed this PDF");
        }
        Review saved = reviewRepository.save(new Review(pdf, user, request.text().trim(), request.consent()));
        return Map.of("reviewId", saved.getId(), "status", "pending approval");
    }

    /** Public: approved reviews only, newest first. */
    @GetMapping("/pdfs/{id}/reviews")
    public List<ReviewView> approvedReviews(@PathVariable Long id) {
        return reviewRepository.findByPdfIdAndApprovedTrueOrderByCreatedAtDesc(id).stream()
                .map(r -> new ReviewView(r.getId(), r.getUser().getName(), r.getText(), r.getCreatedAt()))
                .toList();
    }

    /** Admin only: reviews waiting for approval, oldest first. */
    @GetMapping("/admin/reviews/pending")
    public List<PendingReview> pending() {
        return reviewRepository.findByApprovedFalseOrderByCreatedAtAsc().stream()
                .map(r -> new PendingReview(r.getId(), r.getPdf().getId(), r.getPdf().getTitle(),
                        r.getUser().getName(), r.getText(), r.getCreatedAt()))
                .toList();
    }

    /** Admin only: approve a review so it becomes public. */
    @PutMapping("/admin/reviews/{reviewId}/approve")
    public Map<String, Object> approve(@PathVariable Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        review.approve();
        reviewRepository.save(review);
        return Map.of("reviewId", reviewId, "approved", true);
    }
}
