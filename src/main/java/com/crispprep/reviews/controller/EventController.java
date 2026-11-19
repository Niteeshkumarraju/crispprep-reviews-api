package com.crispprep.reviews.controller;

import com.crispprep.reviews.entity.ActivityEvent;
import com.crispprep.reviews.repository.PdfRepository;
import com.crispprep.reviews.service.ActivityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * The website reports reads and downloads here. Signed in: saved with the user's id.
 * Not signed in: saved without any id (a plain count). No IP address or device details are stored.
 */
@RestController
public class EventController {

    public record EventRequest(@NotBlank @Pattern(regexp = "READ|DOWNLOAD") String type,
                               @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,120}") String slug) { }

    private final ActivityService activity;
    private final PdfRepository pdfs;

    private final com.crispprep.reviews.repository.UserRepository users;
    private final com.crispprep.reviews.service.StreakService streaks;

    public EventController(ActivityService activity, PdfRepository pdfs, com.crispprep.reviews.repository.UserRepository users,
                           com.crispprep.reviews.service.StreakService streaks) {
        this.users = users;
        this.streaks = streaks;
        this.activity = activity;
        this.pdfs = pdfs;
    }

    @PostMapping("/api/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @org.springframework.transaction.annotation.Transactional
    public void record(@Valid @RequestBody EventRequest req, @AuthenticationPrincipal Jwt jwt) {
        if (pdfs.findBySlug(req.slug()).isEmpty()) return;          // only real PDFs are counted
        Long userId = jwt == null ? null : Long.valueOf(jwt.getSubject());
        activity.record(ActivityEvent.Type.valueOf(req.type()), userId, req.slug(), null);
        if (userId != null) users.findById(userId).filter(u -> !u.isBlocked()).ifPresent(streaks::record);   // a study day for the streak
    }
}
