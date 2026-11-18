package com.crispprep.reviews.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"pdf_id", "user_name"}))
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int stars;

    @Column(name = "user_name", nullable = false, length = 50)
    private String userName;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pdf_id")
    private Pdf pdf;

    private Instant createdAt = Instant.now();

    protected Rating() { }

    public Rating(Pdf pdf, String userName, int stars) {
        this.pdf = pdf;
        this.userName = userName;
        this.stars = stars;
    }

    public Long getId() { return id; }
    public int getStars() { return stars; }
    public String getUserName() { return userName; }
    public Pdf getPdf() { return pdf; }
    public Instant getCreatedAt() { return createdAt; }
}