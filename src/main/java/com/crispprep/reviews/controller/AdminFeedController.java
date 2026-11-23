package com.crispprep.reviews.controller;

import com.crispprep.reviews.entity.ActivityEvent;
import com.crispprep.reviews.entity.Pdf;
import com.crispprep.reviews.entity.User;
import com.crispprep.reviews.repository.*;
import com.crispprep.reviews.service.AdminGuard;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Admin panel, phase 3: the live activity feed and CSV exports. */
@RestController
@RequestMapping("/api/admin")
public class AdminFeedController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AdminGuard guard;
    private final ActivityRepository events;
    private final UserRepository users;
    private final PdfRepository pdfs;
    private final AdminUserController adminUsers;   // reuses the PDFs table for the export
    private final com.crispprep.reviews.repository.AdminActionRepository actions;

    public AdminFeedController(AdminGuard guard, ActivityRepository events, UserRepository users, PdfRepository pdfs,
                               AdminUserController adminUsers, com.crispprep.reviews.repository.AdminActionRepository actions) {
        this.actions = actions;
        this.guard = guard;
        this.events = events;
        this.users = users;
        this.pdfs = pdfs;
        this.adminUsers = adminUsers;
    }

    private Map<String, String> titles() {
        return pdfs.findAll().stream().filter(p -> p.getSlug() != null)
                .collect(Collectors.toMap(Pdf::getSlug, Pdf::getTitle, (a, b) -> a));
    }

    private Map<Long, User> usersFor(Collection<ActivityEvent> list) {
        Set<Long> ids = list.stream().map(ActivityEvent::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** Newest events across everyone. Visitors who weren't signed in show as "Visitor". */
    @GetMapping("/activity")
    public Map<String, Object> feed(@RequestParam(required = false) Long before, @RequestParam(defaultValue = "50") int limit,
                                    @AuthenticationPrincipal Jwt jwt) {
        guard.require(jwt);
        List<ActivityEvent> list = events.feed(before, PageRequest.of(0, Math.min(Math.max(limit, 1), 200)));
        Map<Long, User> who = usersFor(list);
        Map<String, String> t = titles();
        List<Map<String, Object>> rows = list.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            User u = e.getUserId() == null ? null : who.get(e.getUserId());
            m.put("id", e.getId());
            m.put("type", e.getType().name());
            m.put("userId", u == null ? null : u.getId());
            m.put("user", u == null ? (e.getUserId() == null ? null : "Deleted user") : u.getName());
            m.put("slug", e.getPdfSlug());
            m.put("title", e.getPdfSlug() == null ? null : t.getOrDefault(e.getPdfSlug(), e.getPdfSlug()));
            m.put("detail", e.getDetail());
            m.put("at", e.getCreatedAt().toString());
            return m;
        }).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("events", rows);
        out.put("more", list.size() == Math.min(Math.max(limit, 1), 200));
        return out;
    }

    // ---------- CSV ----------
    /** One CSV cell: quoted, and a leading = + - @ is defused so Excel never runs it as a formula. */
    static String cell(Object v) {
        String s = v == null ? "" : v.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static ResponseEntity<byte[]> csv(String name, List<String> header, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder("﻿");   // BOM: Excel then reads ₹, ★ and Indian names correctly
        sb.append(String.join(",", header.stream().map(AdminFeedController::cell).toList())).append("\r\n");
        for (List<Object> r : rows) sb.append(r.stream().map(AdminFeedController::cell).collect(Collectors.joining(","))).append("\r\n");
        String file = "crispprep-" + name + "-" + LocalDate.now(IST) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String ist(Instant t) {
        return t == null ? "" : t.atZone(IST).toLocalDateTime().withNano(0).toString().replace('T', ' ');
    }

    /** Exports contain student emails, so every download goes into the admin audit log. */
    private void logExport(User admin, String what) {
        actions.save(new com.crispprep.reviews.entity.AdminAction(admin.getId(), null, "exported " + what));
    }

    @GetMapping("/export/users.csv")
    public ResponseEntity<byte[]> exportUsers(@AuthenticationPrincipal Jwt jwt) {
        logExport(guard.require(jwt), "users.csv");
        List<User> all = users.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        Map<Long, Long> rd = new HashMap<>(), dl = new HashMap<>();
        events.countAllPerUser(ActivityEvent.Type.READ).forEach(c -> rd.put(c.getUserId(), c.getN()));
        events.countAllPerUser(ActivityEvent.Type.DOWNLOAD).forEach(c -> dl.put(c.getUserId(), c.getN()));
        List<List<Object>> rows = all.stream().map(u -> List.<Object>of(
                u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.isBlocked() ? "yes" : "no",
                u.getGoogleId() != null ? (u.getPasswordHash() != null ? "Google + email" : "Google") : "Email",
                ist(u.getCreatedAt()), ist(u.getLastLoginAt()), rd.getOrDefault(u.getId(), 0L), dl.getOrDefault(u.getId(), 0L))).toList();
        return csv("users", List.of("id", "name", "email", "role", "blocked", "signs_in_with", "joined_ist", "last_login_ist", "reads", "downloads"), rows);
    }

    @GetMapping("/export/pdfs.csv")
    public ResponseEntity<byte[]> exportPdfs(@AuthenticationPrincipal Jwt jwt) {
        logExport(guard.require(jwt), "pdfs.csv");
        List<Map<String, Object>> table = adminUsers.pdfTable(jwt);
        List<List<Object>> rows = table.stream().map(p -> List.<Object>of(p.get("slug"), p.get("title"), p.get("subject"),
                p.get("reads"), p.get("downloads"), p.get("average"), p.get("ratings"))).toList();
        return csv("pdfs", List.of("slug", "title", "subject", "reads", "downloads", "average_rating", "ratings"), rows);
    }

    @GetMapping("/export/activity.csv")
    public ResponseEntity<byte[]> exportActivity(@RequestParam(defaultValue = "30") int days, @AuthenticationPrincipal Jwt jwt) {
        User admin = guard.require(jwt);
        int d = Math.min(Math.max(days, 1), 365);
        logExport(admin, "activity.csv (" + d + " days)");
        List<ActivityEvent> list = events.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant.now().minus(Duration.ofDays(d)));
        Map<Long, User> who = usersFor(list);
        Map<String, String> t = titles();
        List<List<Object>> rows = list.stream().map(e -> {
            User u = e.getUserId() == null ? null : who.get(e.getUserId());
            return Arrays.<Object>asList(ist(e.getCreatedAt()), e.getType().name(),
                    u == null ? (e.getUserId() == null ? "Visitor" : "Deleted user") : u.getName(), u == null ? "" : u.getEmail(),
                    e.getPdfSlug() == null ? "" : t.getOrDefault(e.getPdfSlug(), e.getPdfSlug()), e.getDetail());
        }).toList();
        return csv("activity-" + d + "d", List.of("time_ist", "type", "user", "email", "pdf", "detail"), rows);
    }
}
