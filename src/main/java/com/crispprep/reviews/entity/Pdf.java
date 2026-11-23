package com.crispprep.reviews.entity;

import jakarta.persistence.*;

@Entity
public class Pdf {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Same name as on the website: crispprep.pages.dev/pdf/{slug}/ (null for old sample rows). */
    @Column(unique = true, length = 160)
    private String slug;

    @Column(nullable = false)
    private String title;

    private String subject;

    /** Set when the admin clicks Launch for a PDF marked "launch": "admin" in resources.json (null = waiting). */
    @Column(name = "launched_at")
    private java.time.Instant launchedAt;

    protected Pdf() { }

    public Pdf(String title, String subject) {
        this.title = title;
        this.subject = subject;
    }

    public Pdf(String slug, String title, String subject) {
        this(title, subject);
        this.slug = slug;
    }

    public void update(String title, String subject) {
        this.title = title;
        this.subject = subject;
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getSubject() { return subject; }
    public java.time.Instant getLaunchedAt() { return launchedAt; }
    public void launch() { this.launchedAt = java.time.Instant.now(); }
    public void unlaunch() { this.launchedAt = null; }
}
