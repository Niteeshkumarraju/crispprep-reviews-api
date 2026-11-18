package com.crispprep.reviews;

import com.crispprep.reviews.entity.Pdf;
import com.crispprep.reviews.repository.PdfRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {

    private final PdfRepository pdfRepository;

    public DataLoader(PdfRepository pdfRepository) {
        this.pdfRepository = pdfRepository;
    }

    @Override
    public void run(String... args) {
        if (pdfRepository.count() == 0) {
            pdfRepository.save(new Pdf("Simplification & Approximation", "Quant"));
            pdfRepository.save(new Pdf("Seating Arrangement", "Reasoning"));
            pdfRepository.save(new Pdf("Polity Day 6: President & VP", "Polity"));
        }
    }
}