package com.crispprep.reviews.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One thing that happened: a sign-up, login, read, download or rating. Feeds the admin panel.
 * user_id is empty for visitors who are not signed in (they are only counted). No IP address is ever stored.
 * Rows older than 12 months are deleted automatically (ActivityService).
 */
@Entity
@Table(name = "activity_event", indexes = {
        @Index(name = "ix_event_time", columnList = "created_at"),
        @Index(name = "ix_event_user", columnList = "user_id"),
        @Index(name = "ix_event_type_time", columnList = "type, created_at")})
public class ActivityEvent {

    public enum Type { SIGNUP, LOGIN, READ, DOWNLOAD, RATE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Type type;

    @Column(name = "user_id")
    private Long userId;                  // plain id (no foreign key) so deleting a user is simple

    @Column(name = "pdf_slug", length = 120)
    private String pdfSlug;

    @Column(name = "detail", length = 40)
    private String detail;                // e.g. "google" / "email" for logins, "4" for a 4-star rating

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ActivityEvent() { }

    public ActivityEvent(Type type, Long userId, String pdfSlug, String detail) {
        this.type = type;
        this.userId = userId;
        this.pdfSlug = pdfSlug;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public Type getType() { return type; }
    public Long getUserId() { return userId; }
    public String getPdfSlug() { return pdfSlug; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
