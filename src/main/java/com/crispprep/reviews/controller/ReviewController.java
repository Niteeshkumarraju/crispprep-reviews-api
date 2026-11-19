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

    private final com.crispprep.reviews.service.AdminGuard guard;
    private final com.crispprep.reviews.repository.AdminActionRepository actions;

    /** Public name on a review: first name + last initial ("Asha K."), never the email. */
    static String publicName(String full) {
        String[] p = full == null ? new String[0] : full.trim().split("\\s+");
        if (p.length == 0 || p[0].isEmpty()) return "Student";
        return p.length > 1 ? p[0] + " " + Character.toUpperCase(p[p.length - 1].charAt(0)) + "." : p[0];
    }

    public ReviewController(PdfRepository pdfRepository, ReviewRepository reviewRepository, CurrentUser currentUser,
                            com.crispprep.reviews.service.AdminGuard guard, com.crispprep.reviews.repository.AdminActionRepository actions) {
        this.guard = guard;
        this.actions = actions;
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
                .map(r -> new ReviewView(r.getId(), publicName(r.getUser().getName()), r.getText(), r.getCreatedAt()))
                .toList();
    }

    /** Admin only: reviews waiting for approval, oldest first. */
    @GetMapping("/admin/reviews/pending")
    public List<PendingReview> pending(@AuthenticationPrincipal Jwt jwt) {
        guard.require(jwt);
        return reviewRepository.findByApprovedFalseOrderByCreatedAtAsc().stream()
                .map(r -> new PendingReview(r.getId(), r.getPdf().getId(), r.getPdf().getSlug(), r.getPdf().getTitle(),
                        r.getUser().getId(), r.getUser().getName(), r.getUser().getEmail(), r.getText(), r.getCreatedAt()))
                .toList();
    }

    /** Admin only: approve a review so it becomes public. */
    @PutMapping("/admin/reviews/{reviewId}/approve")
    public Map<String, Object> approve(@PathVariable Long reviewId, @AuthenticationPrincipal Jwt jwt) {
        User admin = guard.require(jwt);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        review.approve();
        reviewRepository.save(review);
        actions.save(new com.crispprep.reviews.entity.AdminAction(admin.getId(), review.getUser().getId(),
                "approved review on " + review.getPdf().getTitle()));
        return Map.of("reviewId", reviewId, "approved", true);
    }

    /** Admin only: reject (delete) a review. The student can then write a new one. */
    @DeleteMapping("/admin/reviews/{reviewId}")
    public Map<String, Object> reject(@PathVariable Long reviewId, @AuthenticationPrincipal Jwt jwt) {
        User admin = guard.require(jwt);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        reviewRepository.delete(review);
        actions.save(new com.crispprep.reviews.entity.AdminAction(admin.getId(), review.getUser().getId(),
                (review.isApproved() ? "removed review on " : "rejected review on ") + review.getPdf().getTitle()));
        return Map.of("reviewId", reviewId, "deleted", true);
    }
}
