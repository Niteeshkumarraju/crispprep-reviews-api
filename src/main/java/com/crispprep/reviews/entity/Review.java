package com.crispprep.reviews.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pdf_id")
    private Pdf pdf;

    @Column(name = "user_name", nullable = false, length = 50)
    private String userName;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(nullable = false)
    private boolean consent;

    @Column(nullable = false)
    private boolean approved = false;

    private Instant createdAt = Instant.now();

    protected Review() { }

    public Review(Pdf pdf, String userName, String text, boolean consent) {
        this.pdf = pdf;
        this.userName = userName;
        this.text = text;
        this.consent = consent;
    }

    public void approve() { this.approved = true; }

    public Long getId() { return id; }
    public Pdf getPdf() { return pdf; }
    public String getUserName() { return userName; }
    public String getText() { return text; }
    public boolean isConsent() { return consent; }
    public boolean isApproved() { return approved; }
    public Instant getCreatedAt() { return createdAt; }
}