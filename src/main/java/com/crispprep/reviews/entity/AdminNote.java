package com.crispprep.reviews.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** A private note an admin writes about a user. Only admins can read notes. */
@Entity
@Table(name = "admin_note", indexes = @Index(name = "ix_note_user", columnList = "user_id"))
public class AdminNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "author_name", nullable = false, length = 60)
    private String authorName;

    @Column(nullable = false, length = 1000)
    private String text;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AdminNote() { }

    public AdminNote(Long userId, String authorName, String text) {
        this.userId = userId;
        this.authorName = authorName;
        this.text = text;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getAuthorName() { return authorName; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
