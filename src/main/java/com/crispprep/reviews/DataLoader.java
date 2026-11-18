package com.crispprep.reviews;

import com.crispprep.reviews.entity.Pdf;
import com.crispprep.reviews.repository.PdfRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * At startup, copies the real PDF list from the website (resources.json) into the pdf table,
 * matched by slug, so ratings and reviews use the same PDFs students see on CrispPrep.
 * If the website can't be reached, the app still starts (and gets 3 sample PDFs on a brand-new database).
 */
@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String file, String title, String subject) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResourcesFile(List<Item> items) { }

    private final PdfRepository pdfRepository;
    private final RestClient http;
    private final String sourceUrl;

    public DataLoader(PdfRepository pdfRepository, @Value("${app.pdf-source-url:}") String sourceUrl) {
        this.pdfRepository = pdfRepository;
        this.http = RestClient.create();
        this.sourceUrl = sourceUrl;
    }

    @Override
    public void run(String... args) {
        try {
            if (!sourceUrl.isBlank()) {
                ResourcesFile f = http.get().uri(sourceUrl).retrieve().body(ResourcesFile.class);
                int added = 0, updated = 0;
                for (Item it : f == null || f.items() == null ? List.<Item>of() : f.items()) {
                    if (it.file() == null || !it.file().endsWith(".pdf") || it.title() == null) continue;
                    String slug = it.file().substring(0, it.file().length() - 4);
                    Pdf pdf = pdfRepository.findBySlug(slug).orElse(null);
                    if (pdf == null) { pdfRepository.save(new Pdf(slug, it.title(), it.subject())); added++; }
                    else if (!it.title().equals(pdf.getTitle()) || (it.subject() != null && !it.subject().equals(pdf.getSubject()))) {
                        pdf.update(it.title(), it.subject()); pdfRepository.save(pdf); updated++;
                    }
                }
                log.info("PDF sync from {}: {} added, {} updated", sourceUrl, added, updated);
                return;
            }
        } catch (Exception e) {
            log.warn("PDF sync skipped ({}): {}", sourceUrl, e.getMessage());
        }
        if (pdfRepository.count() == 0) {
            pdfRepository.save(new Pdf("Simplification & Approximation", "Quant"));
            pdfRepository.save(new Pdf("Seating Arrangement", "Reasoning"));
            pdfRepository.save(new Pdf("Polity Day 6: President & VP", "Polity"));
        }
    }
}
