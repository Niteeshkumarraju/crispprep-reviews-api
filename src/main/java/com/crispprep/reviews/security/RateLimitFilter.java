package com.crispprep.reviews.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory rate limits (one server, so no Redis needed). Fixed one-minute windows.
 * - per IP for everything, and tighter per IP for sign-in;
 * - per signed-in user (token) for writes like quiz answers, ratings and reviews;
 * - a site-wide cap on anonymous tracking events and on sign-ins, so even many IPs can't fill the
 *   database or burn the CPU with password checks.
 * Over the limit: HTTP 429 with a friendly message and Retry-After.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    record Rule(String name, int perMinute) { }

    static final Rule IP_ALL = new Rule("ip", 600);
    static final Rule IP_AUTH = new Rule("ip-auth", 30);
    static final Rule GLOBAL_LOGIN = new Rule("all-login", 60);
    static final Rule IP_EVENTS = new Rule("ip-events", 120);
    static final Rule GLOBAL_EVENTS = new Rule("all-events", 1500);
    static final Rule USER_WRITES = new Rule("user-writes", 40);
    static final Rule USER_QUIZ = new Rule("user-quiz", 10);

    private static final int MAX_KEYS = 50_000;
    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();   // key -> {minute, count}

    /** true if this request is still inside the limit (and counts it). */
    boolean allow(Rule rule, String who, long minute) {
        if (windows.size() > MAX_KEYS) windows.entrySet().removeIf(e -> e.getValue()[0] < minute);
        long[] w = windows.compute(rule.name() + "|" + who, (k, v) -> {
            if (v == null || v[0] != minute) return new long[] {minute, 1};
            v[1]++;
            return v;
        });
        return w[1] <= rule.perMinute();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI(), method = req.getMethod();
        if (!path.startsWith("/api/") || "OPTIONS".equals(method)) { chain.doFilter(req, res); return; }
        long minute = System.currentTimeMillis() / 60_000;
        String ip = req.getRemoteAddr();
        String user = userKey(req);
        boolean write = !"GET".equals(method) && !"HEAD".equals(method);

        boolean ok = allow(IP_ALL, ip, minute);
        if (ok && path.startsWith("/api/auth/") && write) {
            ok = allow(IP_AUTH, ip, minute) && allow(GLOBAL_LOGIN, "*", minute);
        }
        if (ok && path.equals("/api/events") && write) {
            ok = allow(IP_EVENTS, ip, minute) && allow(GLOBAL_EVENTS, "*", minute);
        }
        if (ok && write && user != null && !path.startsWith("/api/admin/")) {
            ok = allow(USER_WRITES, user, minute);
            if (ok && path.startsWith("/api/quiz/")) ok = allow(USER_QUIZ, user, minute);
        }
        if (ok) { chain.doFilter(req, res); return; }

        res.setStatus(429);
        res.setHeader("Retry-After", "60");
        res.setContentType("application/json");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("{\"status\":429,\"message\":\"Too many requests. Please wait a minute and try again.\"}");
    }

    /** A short hash of the bearer token (never the token itself) to count per signed-in user. */
    private static String userKey(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ") || h.length() < 20) return null;
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(h.substring(7).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
