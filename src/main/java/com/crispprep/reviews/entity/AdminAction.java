package com.crispprep.reviews.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Audit log: every block, role change or delete an admin makes (who, what, to whom, when). */
@Entity
@Table(name = "admin_action")
public class AdminAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(nullable = false, length = 200)
    private String action;                 // e.g. "blocked asha@example.com"

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AdminAction() { }

    public AdminAction(Long adminId, Long targetUserId, String action) {
        this.adminId = adminId;
        this.targetUserId = targetUserId;
        this.action = action.length() > 200 ? action.substring(0, 200) : action;
    }

    public Long getId() { return id; }
    public Long getAdminId() { return adminId; }
    public Long getTargetUserId() { return targetUserId; }
    public String getAction() { return action; }
    public Instant getCreatedAt() { return createdAt; }
}
