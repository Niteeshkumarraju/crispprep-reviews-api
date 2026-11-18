package com.crispprep.reviews.controller;

import com.crispprep.reviews.dto.ReviewRequest;
import com.crispprep.reviews.dto.ReviewView;
import com.crispprep.reviews.entity.Pdf;
import com.crispprep.reviews.entity.Review;
import com.crispprep.reviews.repository.PdfRepository;
import com.crispprep.reviews.repository.ReviewRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final PdfRepository pdfRepository;
    private final ReviewRepository reviewRepository;

    public ReviewController(PdfRepository pdfRepository, ReviewRepository reviewRepository) {
        this.pdfRepository = pdfRepository;
        this.reviewRepository = reviewRepository;
    }

    @PostMapping("/pdfs/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addReview(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        Pdf pdf = pdfRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not found"));
        Review saved = reviewRepository.save(
                new Review(pdf, request.userName(), request.text(), request.consent()));
        return Map.of("reviewId", saved.getId(), "status", "pending approval");
    }

    @GetMapping("/pdfs/{id}/reviews")
    public List<ReviewView> approvedReviews(@PathVariable Long id) {
        return reviewRepository.findByPdfIdAndApprovedTrueOrderByCreatedAtDesc(id).stream()
                .map(r -> new ReviewView(r.getId(), r.getUserName(), r.getText(), r.getCreatedAt()))
                .toList();
    }

    @PutMapping("/admin/reviews/{reviewId}/approve")
    public Map<String, Object> approve(@PathVariable Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        review.approve();
        reviewRepository.save(review);
        return Map.of("reviewId", reviewId, "approved", true);
    }
}