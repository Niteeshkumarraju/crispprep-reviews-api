package com.crispprep.reviews.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"pdf_id", "user_id"}))
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int stars;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pdf_id")
    private Pdf pdf;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private Instant createdAt = Instant.now();

    protected Rating() { }

    public Rating(Pdf pdf, User user, int stars) {
        this.pdf = pdf;
        this.user = user;
        this.stars = stars;
    }

    /** A user can change their mind: rating again replaces the old stars. */
    public void changeStars(int stars) { this.stars = stars; }

    public Long getId() { return id; }
    public int getStars() { return stars; }
    public Pdf getPdf() { return pdf; }
    public User getUser() { return user; }
    public Instant getCreatedAt() { return createdAt; }
}
