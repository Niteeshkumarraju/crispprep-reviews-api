package com.crispprep.reviews.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    public enum Role { STUDENT, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;          // null for Google-only accounts

    @Column(name = "google_id", unique = true, length = 64)
    private String googleId;              // used by Google sign-in (step 3)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.STUDENT;

    private Instant createdAt = Instant.now();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "blocked")
    private Boolean blocked;              // null or false = allowed; true = blocked by an admin

    protected User() { }

    public User(String email, String name, String passwordHash, Role role) {
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /** Connect a Google account (Google's stable user id, the token's "sub"). */
    public void linkGoogle(String googleId) { this.googleId = googleId; }
    /** After Google proves who owns the email, any password set before that is no longer trusted. */
    public void clearPassword() { this.passwordHash = null; }

    /** Used when ADMIN_EMAIL is set after this account already exists. */
    public void makeAdmin() { this.role = Role.ADMIN; }

    public boolean isBlocked() { return Boolean.TRUE.equals(blocked); }
    public void setBlocked(boolean b) { this.blocked = b; }
    public void setRole(Role role) { this.role = role; }

    public void touchLogin() { this.lastLoginAt = Instant.now(); }
    public Instant getLastLoginAt() { return lastLoginAt; }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPasswordHash() { return passwordHash; }
    public String getGoogleId() { return googleId; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
