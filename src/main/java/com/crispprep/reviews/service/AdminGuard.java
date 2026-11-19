package com.crispprep.reviews.service;

import com.crispprep.reviews.entity.User;
import com.crispprep.reviews.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The token says ADMIN for up to 7 days; this re-checks the database on every admin request,
 * so removing someone's admin role or blocking them takes effect at once.
 */
@Service
public class AdminGuard {

    private final UserRepository users;

    public AdminGuard(UserRepository users) {
        this.users = users;
    }

    public User require(Jwt jwt) {
        User u = jwt == null ? null : users.findById(Long.valueOf(jwt.getSubject())).orElse(null);
        if (u == null || u.isBlocked() || u.getRole() != User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admins only");
        }
        return u;
    }
}
