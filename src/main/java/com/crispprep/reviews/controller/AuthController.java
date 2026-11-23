package com.crispprep.reviews.controller;

import com.crispprep.reviews.dto.AuthResponse;
import com.crispprep.reviews.dto.GoogleLoginRequest;
import com.crispprep.reviews.dto.LoginRequest;
import com.crispprep.reviews.dto.RegisterRequest;
import com.crispprep.reviews.entity.ActivityEvent;
import com.crispprep.reviews.entity.User;
import com.crispprep.reviews.service.ActivityService;
import com.crispprep.reviews.repository.UserRepository;
import com.crispprep.reviews.service.GoogleTokenVerifier;
import com.crispprep.reviews.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final String adminEmail;
    private final GoogleTokenVerifier google;
    private final ActivityService activity;
    private final boolean emailSignup;
    private final com.crispprep.reviews.service.RewardService rewards;
    private final com.crispprep.reviews.repository.UserProgressRepository progressRepo;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                          GoogleTokenVerifier google, ActivityService activity, @Value("${app.admin-email}") String adminEmail,
                          @Value("${app.email-signup:false}") boolean emailSignup,
                          com.crispprep.reviews.service.RewardService rewards, com.crispprep.reviews.repository.UserProgressRepository progressRepo) {
        this.rewards = rewards;
        this.progressRepo = progressRepo;
        this.emailSignup = emailSignup;
        this.activity = activity;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.google = google;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim();
    }

    private AuthResponse response(User u, ActivityEvent.Type what, String how) {
        if (u.isBlocked()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has been blocked. Contact CrispPrep if you think this is a mistake.");
        if (u.getRole() != User.Role.ADMIN && roleFor(u.getEmail()) == User.Role.ADMIN) {   // ADMIN_EMAIL set later
            u.makeAdmin();
        }
        u.touchLogin();
        u = users.save(u);
        activity.record(what, u.getId(), null, how);
        return new AuthResponse(tokens.createToken(u), TokenService.EXPIRES_IN, u.getId(), u.getName(), u.getEmail(), u.getRole().name());
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        if (!emailSignup) {   // only Google-verified emails can create accounts (no fake addresses)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "New accounts use Google sign-in. Please continue with Google.");
        }
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User saved = users.save(new User(email, req.name().trim(), passwordEncoder.encode(req.password()), roleFor(email)));
        applyInvite(saved, req.ref());
        return response(saved, ActivityEvent.Type.SIGNUP, "email");
    }

    @PostMapping("/api/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        User u = users.findByEmail(req.email().trim().toLowerCase())
                .filter(x -> x.getPasswordHash() != null && passwordEncoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong email or password"));
        return response(u, ActivityEvent.Type.LOGIN, "email");
    }

    /** Invited by a friend (new accounts only): the friend's gift is a head start and one streak freeze. */
    private void applyInvite(User user, String ref) {
        if (ref == null || ref.isBlank()) return;
        rewards.userByCode(ref).filter(inviter -> !inviter.getId().equals(user.getId())).ifPresent(inviter -> {
            var p = rewards.of(user);
            p.setReferredBy(inviter.getId(), com.crispprep.reviews.service.RewardService.FRIEND_HEAD_START, com.crispprep.reviews.service.RewardService.FRIEND_GIFT_FREEZES);
            progressRepo.save(p);
        });
    }

    private User.Role roleFor(String email) {
        return !adminEmail.isEmpty() && email.equalsIgnoreCase(adminEmail) ? User.Role.ADMIN : User.Role.STUDENT;
    }

    /** Public: the Google client id the web page needs to show the button (it is not a secret). */
    @GetMapping("/api/auth/google/config")
    public Map<String, Object> googleConfig() {
        return Map.of("enabled", google.enabled(), "clientId", google.clientId());
    }

    /**
     * Sign in with Google. The page sends Google's ID token; we verify it, then find or create the user and
     * return our own JWT. A verified Google email that matches an email/password account is linked to it.
     */
    @PostMapping("/api/auth/google")
    public AuthResponse googleLogin(@Valid @RequestBody GoogleLoginRequest req) {
        if (!google.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in is not configured");
        }
        Jwt g;
        try {
            g = google.verify(req.idToken());
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google sign-in failed, please try again");
        }
        String email = g.getClaimAsString("email");
        if (email == null || !Boolean.TRUE.equals(g.getClaimAsBoolean("email_verified"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your Google email is not verified");
        }
        String googleId = g.getSubject();
        String cleanEmail = email.trim().toLowerCase();
        String name = g.getClaimAsString("name");
        if (name == null || name.isBlank()) name = cleanEmail.substring(0, cleanEmail.indexOf('@'));
        final String displayName = name.length() > 60 ? name.substring(0, 60) : name;

        final boolean isNew = users.findByGoogleId(googleId).isEmpty() && users.findByEmail(cleanEmail).isEmpty();
        User user = users.findByGoogleId(googleId).orElseGet(() ->
                users.findByEmail(cleanEmail).map(existing -> {          // link an existing email/password account
                    existing.linkGoogle(googleId);
                    existing.clearPassword();   // whoever set that password may not own this email
                    return users.save(existing);
                }).orElseGet(() -> {                                     // first time: create a new account
                    User created = new User(cleanEmail, displayName, null, roleFor(cleanEmail));
                    created.linkGoogle(googleId);
                    return users.save(created);
                }));
        if (isNew) applyInvite(user, req.ref());
        return response(user, isNew ? ActivityEvent.Type.SIGNUP : ActivityEvent.Type.LOGIN, "google");
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("userId", jwt.getSubject(), "email", jwt.getClaimAsString("email"),
                "name", jwt.getClaimAsString("name"), "roles", jwt.getClaimAsStringList("roles"));
    }
}
