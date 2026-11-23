package com.crispprep.reviews.controller;

import com.crispprep.reviews.entity.AdminAction;
import com.crispprep.reviews.entity.Pdf;
import com.crispprep.reviews.entity.User;
import com.crispprep.reviews.repository.AdminActionRepository;
import com.crispprep.reviews.repository.PdfRepository;
import com.crispprep.reviews.service.AdminGuard;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * "Launch" for new free PDFs. A PDF added to the website with "launch": "admin" stays hidden until the admin
 * clicks Launch in the panel; then the website shows it in the free section. Unlaunch hides it again.
 */
@RestController
public class LaunchController {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String file, String title, String subject, String launch, Integer pages, String thumb, String added) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResourcesFile(List<Item> items) { }

    private final AdminGuard guard;
    private final PdfRepository pdfs;
    private final AdminActionRepository actions;
    private final RestClient http = RestClient.create();
    private final String sourceUrl;

    public LaunchController(AdminGuard guard, PdfRepository pdfs, AdminActionRepository actions,
                            @Value("${app.pdf-source-url:}") String sourceUrl) {
        this.guard = guard;
        this.pdfs = pdfs;
        this.actions = actions;
        this.sourceUrl = sourceUrl;
    }

    private static String slug(Item it) { return it.file().substring(0, it.file().length() - 4); }

    /** The PDFs on the website that wait for a launch (read live, so new uploads show without a restart). */
    private List<Item> waiting() {
        ResourcesFile f;
        try {
            f = http.get().uri(sourceUrl).retrieve().body(ResourcesFile.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Couldn't read the PDF list from the website. Try again in a minute.");
        }
        if (f == null || f.items() == null) return List.of();
        return f.items().stream().filter(it -> it.file() != null && it.file().endsWith(".pdf") && "admin".equals(it.launch())).toList();
    }

    /** Public: which "launch": "admin" PDFs are live. The website shows only these. */
    @GetMapping("/api/pdfs/launched")
    public List<String> launched() {
        return pdfs.findByLaunchedAtIsNotNull().stream().map(Pdf::getSlug).filter(Objects::nonNull).toList();
    }

    @GetMapping("/api/admin/launches")
    public List<Map<String, Object>> list(@AuthenticationPrincipal Jwt jwt) {
        guard.require(jwt);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Item it : waiting()) {
            Pdf p = pdfs.findBySlug(slug(it)).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("slug", slug(it));
            m.put("file", it.file());
            m.put("title", it.title());
            m.put("subject", it.subject());
            m.put("pages", it.pages());
            m.put("thumb", it.thumb());
            m.put("added", it.added());
            m.put("launchedAt", p == null || p.getLaunchedAt() == null ? null : p.getLaunchedAt().toString());
            out.add(m);
        }
        return out;
    }

    @PostMapping("/api/admin/launches/{slug}")
    @Transactional
    public Map<String, Object> launch(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        User admin = guard.require(jwt);
        Item it = waiting().stream().filter(x -> slug(x).equals(slug)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This PDF is not waiting for a launch"));
        Pdf p = pdfs.findBySlug(slug).orElseGet(() -> pdfs.save(new Pdf(slug, it.title(), it.subject())));
        p.launch();
        pdfs.save(p);
        actions.save(new AdminAction(admin.getId(), null, "launched PDF " + slug));
        return Map.of("slug", slug, "launchedAt", p.getLaunchedAt().toString());
    }

    @DeleteMapping("/api/admin/launches/{slug}")
    @Transactional
    public Map<String, Object> unlaunch(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        User admin = guard.require(jwt);
        Pdf p = pdfs.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not found"));
        p.unlaunch();
        pdfs.save(p);
        actions.save(new AdminAction(admin.getId(), null, "hid PDF " + slug));
        return Map.of("slug", slug, "launched", false);
    }
}
