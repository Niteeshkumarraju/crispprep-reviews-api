package com.crispprep.reviews.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/** Streak, referral and reward state for one user (created on first use). Days are IST calendar days. */
@Entity
@Table(name = "user_progress", indexes = @Index(name = "ix_progress_referred_by", columnList = "referred_by"))
public class UserProgress {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "streak_current") private Integer streakCurrent = 0;
    @Column(name = "streak_best") private Integer streakBest = 0;
    @Column(name = "last_day") private LocalDate lastDay;              // last IST day that counted
    @Column(name = "freezes") private Integer freezes = 0;              // saves the streak after one missed day
    @Column(name = "ref_code", unique = true, length = 16) private String refCode;
    @Column(name = "referred_by") private Long referredBy;              // who invited this user
    @Column(name = "referral_qualified_at") private Instant referralQualifiedAt;   // set when this user first studied
    @Column(name = "head_start") private Integer headStart = 0;         // extra days for an invited friend's first day
    @Column(name = "credits") private Integer credits = 0;              // "pick any book" credits from referrals
    @Column(name = "all_access") private Boolean allAccess = false;     // 5 referrals: every book, present and future

    protected UserProgress() { }
    public UserProgress(Long userId, String refCode) { this.userId = userId; this.refCode = refCode; }

    private static int n(Integer v) { return v == null ? 0 : v; }
    public Long getUserId() { return userId; }
    public int getStreakCurrent() { return n(streakCurrent); }
    public int getStreakBest() { return n(streakBest); }
    public LocalDate getLastDay() { return lastDay; }
    public int getFreezes() { return n(freezes); }
    public String getRefCode() { return refCode; }
    public Long getReferredBy() { return referredBy; }
    public Instant getReferralQualifiedAt() { return referralQualifiedAt; }
    public int getHeadStart() { return n(headStart); }
    public int getCredits() { return n(credits); }
    public boolean isAllAccess() { return Boolean.TRUE.equals(allAccess); }

    public void setStreak(int current, LocalDate day) { streakCurrent = current; lastDay = day; if (current > getStreakBest()) streakBest = current; }
    public void addFreezes(int k, int max) { freezes = Math.min(max, getFreezes() + k); }
    public void useFreeze() { freezes = Math.max(0, getFreezes() - 1); }
    public void setReferredBy(Long id, int headStartDays, int giftFreezes) { referredBy = id; headStart = headStartDays; freezes = getFreezes() + giftFreezes; }
    public void markQualified() { referralQualifiedAt = Instant.now(); }
    public void clearHeadStart() { headStart = 0; }
    public void addCredits(int k) { credits = getCredits() + k; }
    public void useCredit() { credits = Math.max(0, getCredits() - 1); }
    public void grantAllAccess() { allAccess = true; }
}
