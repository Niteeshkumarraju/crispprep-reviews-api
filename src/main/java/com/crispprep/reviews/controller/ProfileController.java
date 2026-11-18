package com.crispprep.reviews.controller;

import com.crispprep.reviews.entity.User;
import com.crispprep.reviews.repository.RatingRepository;
import com.crispprep.reviews.repository.ReviewRepository;
import com.crispprep.reviews.service.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** The website's profile section: who you are, how you sign in, and what you have rated or reviewed. */
@RestController
public class ProfileController {

    private final CurrentUser currentUser;
    private final RatingRepository ratings;
    private final ReviewRepository reviews;

    public ProfileController(CurrentUser currentUser, RatingRepository ratings, ReviewRepository reviews) {
        this.currentUser = currentUser;
        this.ratings = ratings;
        this.reviews = reviews;
    }

    @GetMapping("/api/me/profile")
    public Map<String, Object> profile(@AuthenticationPrincipal Jwt jwt) {
        User u = currentUser.from(jwt);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("userId", u.getId());
        p.put("name", u.getName());
        p.put("email", u.getEmail());
        p.put("role", u.getRole().name());
        p.put("memberSince", u.getCreatedAt() == null ? null : u.getCreatedAt().toString());
        p.put("google", u.getGoogleId() != null);
        p.put("password", u.getPasswordHash() != null);
        p.put("ratings", ratings.countByUserId(u.getId()));
        p.put("reviews", reviews.countByUserId(u.getId()));
        return p;
    }
}
