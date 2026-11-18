package com.crispprep.reviews.controller;

import com.crispprep.reviews.dto.RatingRequest;
import com.crispprep.reviews.dto.TopPdf;
import com.crispprep.reviews.entity.Pdf;
import com.crispprep.reviews.entity.Rating;
import com.crispprep.reviews.repository.PdfRepository;
import com.crispprep.reviews.repository.RatingRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pdfs")
public class PdfController {

    private final PdfRepository pdfRepository;
    private final RatingRepository ratingRepository;

    public PdfController(PdfRepository pdfRepository, RatingRepository ratingRepository) {
        this.pdfRepository = pdfRepository;
        this.ratingRepository = ratingRepository;
    }

    private Pdf findPdf(Long id) {
        return pdfRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not found"));
    }

    @GetMapping
    public List<Pdf> listPdfs() {
        return pdfRepository.findAll();
    }

    @PostMapping("/{id}/ratings")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addRating(@PathVariable Long id, @Valid @RequestBody RatingRequest request) {
        Pdf pdf = findPdf(id);
        if (ratingRepository.existsByPdfIdAndUserName(id, request.userName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already rated this PDF");
        }
        Rating saved = ratingRepository.save(new Rating(pdf, request.userName(), request.stars()));
        return Map.of("ratingId", saved.getId(), "pdfId", pdf.getId(),
                "userName", saved.getUserName(), "stars", saved.getStars());
    }

    @GetMapping("/{id}/summary")
    public Map<String, Object> summary(@PathVariable Long id) {
        Pdf pdf = findPdf(id);
        Double avg = ratingRepository.averageForPdf(id);
        long count = ratingRepository.countByPdfId(id);
        double rounded = avg == null ? 0.0 : Math.round(avg * 100) / 100.0;
        return Map.of("pdfId", id, "title", pdf.getTitle(), "average", rounded, "count", count);
    }

    @GetMapping("/top")
    public List<TopPdf> topRated() {
        return ratingRepository.findTopRated();
    }
}