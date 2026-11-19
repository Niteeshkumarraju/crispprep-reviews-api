package com.crispprep.reviews.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/** One user's Daily Quiz result for one IST day (one attempt per day). */
@Entity
@Table(name = "quiz_attempt", uniqueConstraints = @UniqueConstraint(name = "uk_quiz_user_day", columnNames = {"user_id", "day"}))
public class QuizAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false) private LocalDate day;
    @Column(nullable = false) private Integer score;
    @Column(nullable = false) private Integer total;
    @Column(nullable = false, length = 40) private String answers;   // e.g. "1,0,3,2,1"
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    protected QuizAttempt() { }
    public QuizAttempt(Long userId, LocalDate day, int score, int total, String answers) { this.userId = userId; this.day = day; this.score = score; this.total = total; this.answers = answers; }
    public Long getUserId() { return userId; }
    public LocalDate getDay() { return day; }
    public int getScore() { return score; }
    public int getTotal() { return total; }
    public String getAnswers() { return answers; }
}
