package com.crispprep.reviews.controller;

import com.crispprep.reviews.entity.*;
import com.crispprep.reviews.repository.*;
import com.crispprep.reviews.service.AdminGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/** Admin panel, phase 2: one user's details, notes, block / admin / delete, and the PDFs table. */
@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    public record NoteRequest(@NotBlank @Size(max = 1000) String text) { }
    public record BlockRequest(boolean blocked) { }
    public record RoleRequest(@NotBlank String role) { }

    private final AdminGuard guard;
    private final UserRepository users;
    private final ActivityRepository events;
    private final RatingRepository ratings;
    private final ReviewRepository reviews;
    private final AdminNoteRepository notes;
    private final AdminActionRepository actions;
    private final PdfRepository pdfs;
    private final String ownerEmail;
    private final com.crispprep.reviews.repository.UserProgressRepository progressRepo;
    private final com.crispprep.reviews.repository.RewardGrantRepository grantRepo;

    public AdminUserController(AdminGuard guard, UserRepository users, ActivityRepository events, RatingRepository ratings,
                               ReviewRepository reviews, AdminNoteRepository notes, AdminActionRepository actions,
                               PdfRepository pdfs, @Value("${app.admin-email}") String ownerEmail,
                               com.crispprep.reviews.repository.UserProgressRepository progressRepo, com.crispprep.reviews.repository.RewardGrantRepository grantRepo) {
        this.progressRepo = progressRepo;
        this.grantRepo = grantRepo;
        this.guard = guard;
        this.users = users;
        this.events = events;
        this.ratings = ratings;
        this.reviews = reviews;
        this.notes = notes;
        this.actions = actions;
        this.pdfs = pdfs;
        this.ownerEmail = ownerEmail == null ? "" : ownerEmail.trim();
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Map<String, String> titles() {
        return pdfs.findAll().stream().filter(p -> p.getSlug() != null)
                .collect(Collectors.toMap(Pdf::getSlug, Pdf::getTitle, (a, b) -> a));
    }

    /** The site owner (ADMIN_EMAIL) and yourself can't be blocked, demoted or deleted from the panel. */
    private void protect(User admin, User target) {
        if (target.getId().equals(admin.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can't do this to your own account.");
        }
        if (!ownerEmail.isEmpty() && target.getEmail().equalsIgnoreCase(ownerEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The site owner's account can't be changed here.");
        }
    }

    private static String iso(java.time.Instant t) { return t == null ? null : t.toString(); }

    @GetMapping("/users/{id}")
    public Map<String, Object> user(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        guard.require(jwt);
        User u = find(id);
        Map<String, String> t = titles();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", u.getId());
        out.put("name", u.getName());
        out.put("email", u.getEmail());
        out.put("role", u.getRole().name());
        out.put("blocked", u.isBlocked());
        out.put("owner", !ownerEmail.isEmpty() && u.getEmail().equalsIgnoreCase(ownerEmail));
        out.put("joined", iso(u.getCreatedAt()));
        out.put("lastLogin", iso(u.getLastLoginAt()));
        out.put("google", u.getGoogleId() != null);
        out.put("password", u.getPasswordHash() != null);
        out.put("reads", events.countPerUser(ActivityEvent.Type.READ, List.of(id)).stream().mapToLong(UserCount::getN).sum());
        out.put("downloads", events.countPerUser(ActivityEvent.Type.DOWNLOAD, List.of(id)).stream().mapToLong(UserCount::getN).sum());
        out.put("ratings", ratings.findByUserIdOrderByCreatedAtDesc(id).stream().map(r -> Map.<String, Object>of(
                "slug", Objects.toString(r.getPdf().getSlug(), ""), "title", r.getPdf().getTitle(), "stars", r.getStars(), "at", iso(r.getCreatedAt()))).toList());
        out.put("reviews", reviews.findByUserIdOrderByCreatedAtDesc(id).stream().map(r -> Map.<String, Object>of(
                "title", r.getPdf().getTitle(), "text", r.getText(), "approved", r.isApproved(), "at", iso(r.getCreatedAt()))).toList());
        out.put("activity", events.findTop100ByUserIdOrderByCreatedAtDesc(id).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", e.getType().name());
            m.put("slug", e.getPdfSlug());
            m.put("title", e.getPdfSlug() == null ? null : t.getOrDefault(e.getPdfSlug(), e.getPdfSlug()));
            m.put("detail", e.getDetail());
            m.put("at", iso(e.getCreatedAt()));
            return m;
        }).toList());
        out.put("notes", notes.findByUserIdOrderByCreatedAtDesc(id).stream().map(this::note).toList());
        progressRepo.findById(id).ifPresent(p -> {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("streak", com.crispprep.reviews.service.StreakService.shown(p)); g.put("best", p.getStreakBest()); g.put("freezes", p.getFreezes());
            g.put("refCode", p.getRefCode()); g.put("friends", progressRepo.countByReferredByAndReferralQualifiedAtIsNotNull(id));
            g.put("invitedBy", p.getReferredBy() == null ? null : users.findById(p.getReferredBy()).map(User::getName).orElse("Deleted user"));
            g.put("credits", p.getCredits()); g.put("allAccess", p.isAllAccess());
            g.put("rewards", grantRepo.findByUserId(id).stream().map(r -> Map.of("slug", r.getSlug(), "reason", r.getReason(), "at", iso(r.getCreatedAt()))).toList());
            out.put("progress", g);
        });
        out.put("adminLog", actions.findTop20ByTargetUserIdOrderByCreatedAtDesc(id).stream()
                .map(a -> Map.<String, Object>of("action", a.getAction(), "at", iso(a.getCreatedAt()))).toList());
        return out;
    }

    private Map<String, Object> note(AdminNote n) {
        return Map.of("id", n.getId(), "text", n.getText(), "by", n.getAuthorName(), "at", iso(n.getCreatedAt()));
    }

    @PostMapping("/users/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addNote(@PathVariable Long id, @Valid @RequestBody NoteRequest req, @AuthenticationPrincipal Jwt jwt) {
        User admin = guard.require(jwt);
        find(id);
        return note(notes.save(new AdminNote(id, admin.getName(), req.text().trim())));
    }

    @DeleteMapping("/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNote(@PathVariable Long noteId, @AuthenticationPrincipal Jwt jwt) {
        guard.require(jwt);
        notes.deleteById(noteId);
    }

    @PostMapping("/users/{id}/block")
    public Map<String, Object> block(@PathVariable Long id, @RequestBody BlockRequest req, @AuthenticationPrincipal Jwt jwt) {
        User admin = guard.require(jwt);
        User u = find(id);
        protect(admin, u);
        u.setBlocked(req.blocked());
        users.save(u);
        actions.save(new AdminAction(admin.getId(), u.getId(), (req.blocked() ? "blocked " : "unblocked ") + u.getEmail()));
        return Map.of("id", u.getId(), "blocked", u.isBlocked());
    }

    @PostMapping("/users/{id}/role")
    public Map<String, Object> role(@PathVariable Long id, @Valid @RequestBody RoleRequest req, @AuthenticationPrincipal Jwt jwt) {
        User admin = guard.require(jwt);
        User u = find(id);
        protect(admin, u);
        User.Role role;
        try { role = User.Role.valueOf(req.role()); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be STUDENT or ADMIN"); }
        u.setRole(role);
        users.save(u);
        actions.save(new AdminAction(admin.getId(), u.getId(), "set role " + role + " for " + u.getEmail()));
        return Map.of("id", u.getId(), "role", role.name());
    }

    /** Deletes the account and everything linked to it (ratings, reviews, notes, activity). Cannot be undone. */
    @DeleteMapping("/users/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        User admin = guard.require(jwt);
        User u = find(id);
        protect(admin, u);
        String email = u.getEmail();
        ratings.deleteForUser(id);
        reviews.deleteForUser(id);
        notes.deleteForUser(id);
        events.deleteForUser(id);
        grantRepo.deleteAll(grantRepo.findByUserId(id));
        progressRepo.findById(id).ifPresent(progressRepo::delete);
        users.delete(u);
        actions.save(new AdminAction(admin.getId(), null, "deleted account " + email));
        return Map.of("deleted", email);
    }

    /** Every PDF with reads, downloads and ratings (all time). */
    @GetMapping("/pdfs")
    public List<Map<String, Object>> pdfTable(@AuthenticationPrincipal Jwt jwt) {
        guard.require(jwt);
        Map<String, long[]> use = new HashMap<>();
        events.countBySlugAndType(List.of(ActivityEvent.Type.READ, ActivityEvent.Type.DOWNLOAD)).forEach(r ->
                use.computeIfAbsent(r.getSlug(), k -> new long[2])[r.getType() == ActivityEvent.Type.READ ? 0 : 1] += r.getN());
        Map<String, RatingRepository.SlugRating> rated = ratings.allBySlug().stream()
                .collect(Collectors.toMap(RatingRepository.SlugRating::getSlug, r -> r));
        return pdfs.findAll().stream().filter(p -> p.getSlug() != null).map(p -> {
            long[] c = use.getOrDefault(p.getSlug(), new long[2]);
            RatingRepository.SlugRating r = rated.get(p.getSlug());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("slug", p.getSlug());
            m.put("title", p.getTitle());
            m.put("subject", p.getSubject());
            m.put("reads", c[0]);
            m.put("downloads", c[1]);
            m.put("average", r == null ? 0.0 : Math.round(r.getAverage() * 10) / 10.0);
            m.put("ratings", r == null ? 0L : r.getRatings());
            return m;
        }).toList();
    }
}
