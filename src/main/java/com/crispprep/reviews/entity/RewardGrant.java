package com.crispprep.reviews.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** One book a user has unlocked, and why. Unlocked books stay unlocked forever. */
@Entity
@Table(name = "reward_grant", uniqueConstraints = @UniqueConstraint(name = "uk_grant_user_slug", columnNames = {"user_id", "slug"}))
public class RewardGrant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 40) private String slug;
    @Column(nullable = false, length = 60) private String reason;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    protected RewardGrant() { }
    public RewardGrant(Long userId, String slug, String reason) { this.userId = userId; this.slug = slug; this.reason = reason; }
    public Long getUserId() { return userId; }
    public String getSlug() { return slug; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
