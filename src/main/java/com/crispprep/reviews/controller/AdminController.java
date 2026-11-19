package com.crispprep.reviews.controller;

import com.crispprep.reviews.entity.ActivityEvent.Type;
import com.crispprep.reviews.entity.Pdf;
import com.crispprep.reviews.entity.User;
import com.crispprep.reviews.repository.*;
import com.crispprep.reviews.service.AdminGuard;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The admin panel's data. Everything under /api/admin needs the ADMIN role (SecurityConfig). */
@RestController
public class AdminController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final UserRepository users;
    private final ActivityRepository events;
    private final RatingRepository ratings;
    private final ReviewRepository reviews;
    private final PdfRepository pdfs;
    private final AdminGuard guard;

    public AdminController(UserRepository users, ActivityRepository events, RatingRepository ratings,
                           ReviewRepository reviews, PdfRepository pdfs, AdminGuard guard) {
        this.guard = guard;
        this.users = users;
        this.events = events;
        this.ratings = ratings;
        this.reviews = reviews;
        this.pdfs = pdfs;
    }

    private static Instant daysAgoIst(int days) {   // start of the day (IST), `days` days back; 0 = today
        return LocalDate.now(IST).minusDays(days).atStartOfDay(IST).toInstant();
    }

    private static Map<String, Object> period(long today, long week, long month, long total) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("today", today); m.put("week", week); m.put("month", month); m.put("total", total);
        return m;
    }

    private Map<String, Object> eventPeriod(Type t) {
        return period(events.countByTypeAndCreatedAtGreaterThanEqual(t, daysAgoIst(0)),
                events.countByTypeAndCreatedAtGreaterThanEqual(t, daysAgoIst(6)),
                events.countByTypeAndCreatedAtGreaterThanEqual(t, daysAgoIst(29)),
                events.countByType(t));
    }

    @GetMapping("/api/admin/stats")
    public Map<String, Object> stats(@AuthenticationPrincipal Jwt jwt) {
        guard.require(jwt);
        Map<String, String> titles = pdfs.findAll().stream().filter(p -> p.getSlug() != null)
                .collect(Collectors.toMap(Pdf::getSlug, Pdf::getTitle, (a, b) -> a));
        Map<String, Object> out = new LinkedHashMap<>();

        out.put("users", period(users.countByCreatedAtGreaterThanEqual(daysAgoIst(0)), users.countByCreatedAtGreaterThanEqual(daysAgoIst(6)),
                users.countByCreatedAtGreaterThanEqual(daysAgoIst(29)), users.count()));
        out.put("google", users.countByGoogleIdIsNotNull());
        out.put("emailPassword", users.countByPasswordHashIsNotNull());
        out.put("activeUsers7d", events.activeUsersSince(daysAgoIst(6)));
        out.put("reads", eventPeriod(Type.READ));
        out.put("downloads", eventPeriod(Type.DOWNLOAD));
        out.put("logins", eventPeriod(Type.LOGIN));
        Double avg = ratings.averageAll();
        out.put("ratings", Map.of("total", ratings.count(), "average", avg == null ? 0.0 : Math.round(avg * 10) / 10.0));
        out.put("pendingReviews", reviews.countByApprovedFalse());

        // last 30 days, one row per IST day: sign-ups, reads, downloads
        LocalDate first = LocalDate.now(IST).minusDays(29);
        Map<LocalDate, long[]> days = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) days.put(first.plusDays(i), new long[3]);
        users.findByCreatedAtGreaterThanEqual(daysAgoIst(29)).forEach(u -> {
            long[] d = days.get(LocalDate.ofInstant(u.getCreatedAt(), IST)); if (d != null) d[0]++;
        });
        events.timesSince(daysAgoIst(29), List.of(Type.READ, Type.DOWNLOAD)).forEach(e -> {
            long[] d = days.get(LocalDate.ofInstant(e.getCreatedAt(), IST)); if (d != null) d[e.getType() == Type.READ ? 1 : 2]++;
        });
        out.put("daily", days.entrySet().stream().map(e -> Map.of("date", e.getKey().toString(),
                "signups", e.getValue()[0], "reads", e.getValue()[1], "downloads", e.getValue()[2])).toList());

        // what students use most this week, and the best / weakest rated PDFs (2+ ratings for "weakest")
        Function<Type, List<Map<String, Object>>> top = t -> events.topSlugs(t, daysAgoIst(6), PageRequest.of(0, 5)).stream()
                .map(r -> Map.<String, Object>of("slug", r.getSlug(), "title", titles.getOrDefault(r.getSlug(), r.getSlug()), "count", r.getN()))
                .toList();
        out.put("topDownloads7d", top.apply(Type.DOWNLOAD));
        out.put("topReads7d", top.apply(Type.READ));
        List<Map<String, Object>> rated = ratings.allBySlug().stream()
                .map(r -> Map.<String, Object>of("slug", r.getSlug(), "title", titles.getOrDefault(r.getSlug(), r.getSlug()),
                        "average", Math.round(r.getAverage() * 10) / 10.0, "count", r.getRatings()))
                .collect(Collectors.toCollection(ArrayList::new));
        rated.sort(Comparator.comparing((Map<String, Object> m) -> (Double) m.get("average")).reversed()
                .thenComparing(m -> -(Long) m.get("count")));
        out.put("topRated", rated.stream().limit(5).toList());
        out.put("lowestRated", rated.stream().filter(m -> (Long) m.get("count") >= 2)
                .sorted(Comparator.comparing(m -> (Double) m.get("average"))).limit(5).toList());
        return out;
    }

    /** Users list: search by name/email, newest first, 25 per page, with each user's activity counts. */
    @GetMapping("/api/admin/users")
    public Map<String, Object> users(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                                     @AuthenticationPrincipal Jwt jwt) {
        guard.require(jwt);
        String query = q.trim().toLowerCase(Locale.ROOT);
        if (query.length() > 120) query = query.substring(0, 120);
        Page<User> p = users.search(query, PageRequest.of(Math.max(page, 0), 25, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<Long> ids = p.getContent().stream().map(User::getId).toList();
        Map<Long, Long> dl = ids.isEmpty() ? Map.of() : toMap(events.countPerUser(Type.DOWNLOAD, ids));
        Map<Long, Long> rd = ids.isEmpty() ? Map.of() : toMap(events.countPerUser(Type.READ, ids));
        Map<Long, Long> rt = ids.isEmpty() ? Map.of() : toMap(ratings.countPerUser(ids));
        List<Map<String, Object>> rows = p.getContent().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("name", u.getName());
            m.put("email", u.getEmail());
            m.put("role", u.getRole().name());
            m.put("blocked", u.isBlocked());
            m.put("joined", u.getCreatedAt() == null ? null : u.getCreatedAt().toString());
            m.put("lastLogin", u.getLastLoginAt() == null ? null : u.getLastLoginAt().toString());
            m.put("method", u.getGoogleId() != null && u.getPasswordHash() != null ? "Google + email"
                    : u.getGoogleId() != null ? "Google" : "Email");
            m.put("downloads", dl.getOrDefault(u.getId(), 0L));
            m.put("reads", rd.getOrDefault(u.getId(), 0L));
            m.put("ratings", rt.getOrDefault(u.getId(), 0L));
            return m;
        }).toList();
        return Map.of("users", rows, "page", p.getNumber(), "pages", p.getTotalPages(), "total", p.getTotalElements());
    }

    private static Map<Long, Long> toMap(List<UserCount> rows) {
        return rows.stream().collect(Collectors.toMap(UserCount::getUserId, UserCount::getN));
    }
}
