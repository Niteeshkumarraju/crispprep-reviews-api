package com.crispprep.reviews.security;

import com.crispprep.reviews.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Runs once at every start: Google-linked accounts must not keep an old password (safe to repeat). */
@Component
public class StartupCleanup implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupCleanup.class);
    private final UserRepository users;

    public StartupCleanup(UserRepository users) { this.users = users; }

    @Override
    public void run(ApplicationArguments args) {
        int n = users.clearPasswordsOfGoogleAccounts();
        if (n > 0) log.info("Removed old passwords from {} Google-linked account(s)", n);
    }
}
