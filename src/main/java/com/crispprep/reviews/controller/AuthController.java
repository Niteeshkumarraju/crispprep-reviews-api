package com.crispprep.reviews.controller;

import com.crispprep.reviews.dto.AuthResponse;
import com.crispprep.reviews.dto.LoginRequest;
import com.crispprep.reviews.dto.RegisterRequest;
import com.crispprep.reviews.entity.User;
import com.crispprep.reviews.repository.UserRepository;
import com.crispprep.reviews.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final String adminEmail;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                          @Value("${app.admin-email}") String adminEmail) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim();
    }

    private AuthResponse response(User u) {
        return new AuthResponse(tokens.createToken(u), TokenService.EXPIRES_IN, u.getId(), u.getName(), u.getEmail(), u.getRole().name());
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User.Role role = !adminEmail.isEmpty() && email.equalsIgnoreCase(adminEmail) ? User.Role.ADMIN : User.Role.STUDENT;
        User saved = users.save(new User(email, req.name().trim(), passwordEncoder.encode(req.password()), role));
        return response(saved);
    }

    @PostMapping("/api/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        User u = users.findByEmail(req.email().trim().toLowerCase())
                .filter(x -> x.getPasswordHash() != null && passwordEncoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong email or password"));
        return response(u);
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("userId", jwt.getSubject(), "email", jwt.getClaimAsString("email"),
                "name", jwt.getClaimAsString("name"), "roles", jwt.getClaimAsStringList("roles"));
    }
}
