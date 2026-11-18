package com.crispprep.reviews.controller;

import com.crispprep.reviews.dto.RatingRequest;
import com.crispprep.reviews.dto.TopPdf;
import com.crispprep.reviews.entity.Pdf;
import com.crispprep.reviews.entity.Rating;
import com.crispprep.reviews.entity.User;
import com.crispprep.reviews.repository.PdfRepository;
import com.crispprep.reviews.repository.RatingRepository;
import com.crispprep.reviews.service.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pdfs")
public class PdfController {

    private final PdfRepository pdfRepository;
    private final RatingRepository ratingRepository;
    private final CurrentUser currentUser;

    public PdfController(PdfRepository pdfRepository, RatingRepository ratingRepository, CurrentUser currentUser) {
        this.pdfRepository = pdfRepository;
        this.ratingRepository = ratingRepository;
        this.currentUser = currentUser;
    }

    private Pdf findPdf(Long id) {
        return pdfRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not found"));
    }

    @GetMapping
    public List<Pdf> listPdfs() {
        return pdfRepository.findAll();
    }

    /** Rate a PDF as the logged-in user (needs a token). One rating per user per PDF. */
    @PostMapping("/{id}/ratings")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addRating(@PathVariable Long id, @Valid @RequestBody RatingRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        Pdf pdf = findPdf(id);
        User user = currentUser.from(jwt);
        if (ratingRepository.existsByPdfIdAndUserId(id, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already rated this PDF");
        }
        Rating saved = ratingRepository.save(new Rating(pdf, user, request.stars()));
        return Map.of("ratingId", saved.getId(), "pdfId", pdf.getId(), "user", user.getName(), "stars", saved.getStars());
    }

    /** Public: average rating and count. With a token, also returns the caller's own rating. */
    @GetMapping("/{id}/summary")
    public Map<String, Object> summary(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Pdf pdf = findPdf(id);
        Double avg = ratingRepository.averageForPdf(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pdfId", id);
        out.put("title", pdf.getTitle());
        out.put("average", avg == null ? 0.0 : Math.round(avg * 100) / 100.0);
        out.put("count", ratingRepository.countByPdfId(id));
        if (jwt != null) {
            out.put("myRating", ratingRepository.findByPdfIdAndUserId(id, Long.valueOf(jwt.getSubject()))
                    .map(Rating::getStars).orElse(null));
        }
        return out;
    }

    @GetMapping("/top")
    public List<TopPdf> topRated() {
        return ratingRepository.findTopRated();
    }
}
