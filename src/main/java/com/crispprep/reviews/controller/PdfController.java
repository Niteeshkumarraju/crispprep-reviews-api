package com.crispprep.reviews.controller;

import com.crispprep.reviews.dto.RatingRequest;
import com.crispprep.reviews.dto.TopPdf;
import com.crispprep.reviews.entity.ActivityEvent;
import com.crispprep.reviews.entity.Pdf;
import com.crispprep.reviews.entity.Rating;
import com.crispprep.reviews.entity.User;
import com.crispprep.reviews.repository.PdfRepository;
import com.crispprep.reviews.repository.RatingRepository;
import com.crispprep.reviews.service.ActivityService;
import com.crispprep.reviews.service.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/pdfs")
public class PdfController {

    private final PdfRepository pdfRepository;
    private final RatingRepository ratingRepository;
    private final CurrentUser currentUser;

    private final ActivityService activity;

    public PdfController(PdfRepository pdfRepository, RatingRepository ratingRepository, CurrentUser currentUser, ActivityService activity) {
        this.activity = activity;
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

    /** Rate a PDF as the logged-in user (needs a token). One rating per user per PDF; rating again changes it. */
    @PostMapping("/{id}/ratings")
    public ResponseEntity<Map<String, Object>> addRating(@PathVariable Long id, @Valid @RequestBody RatingRequest request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        Pdf pdf = findPdf(id);
        User user = currentUser.from(jwt);
        Optional<Rating> old = ratingRepository.findByPdfIdAndUserId(id, user.getId());
        Rating saved;
        if (old.isPresent()) {
            old.get().changeStars(request.stars());
            saved = ratingRepository.save(old.get());
        } else {
            saved = ratingRepository.save(new Rating(pdf, user, request.stars()));
        }
        activity.record(ActivityEvent.Type.RATE, user.getId(), pdf.getSlug(), String.valueOf(saved.getStars()));
        Map<String, Object> out = summary(id, jwt);
        out.put("myRating", saved.getStars());
        return ResponseEntity.status(old.isPresent() ? HttpStatus.OK : HttpStatus.CREATED).body(out);
    }

    /** Public: average + count for every rated PDF, by slug, e.g. {"Polity-Day-02-Preamble": {"average": 4.5, "count": 2}}. */
    @GetMapping("/ratings")
    public Map<String, Object> allRatings() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (RatingRepository.SlugRating r : ratingRepository.allBySlug()) {
            out.put(r.getSlug(), Map.of("average", Math.round(r.getAverage() * 10) / 10.0, "count", r.getRatings()));
        }
        return out;
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

    /** Same as summary, looked up by the website's slug (what the PDF pages know). */
    @GetMapping("/slug/{slug}/summary")
    public Map<String, Object> summaryBySlug(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        Pdf pdf = pdfRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not found"));
        Map<String, Object> out = summary(pdf.getId(), jwt);
        out.put("slug", slug);
        return out;
    }

    @GetMapping("/top")
    public List<TopPdf> topRated() {
        return ratingRepository.findTopRated();
    }
}
