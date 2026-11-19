package com.crispprep.reviews.service;

import com.crispprep.reviews.entity.User;
import com.crispprep.reviews.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Turns the logged-in user's token into the User row (the token's "sub" claim is the user id). */
@Service
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public User from(Jwt jwt) {
        User u = users.findById(Long.valueOf(jwt.getSubject()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found, please log in again"));
        if (u.isBlocked()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has been blocked.");
        return u;
    }
}
