package com.crispprep.reviews.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/** A premium reward PDF, stored in the database (never on the public website). Uploaded from the admin panel. */
@Entity
@Table(name = "reward_file")
public class RewardFile {

    @Id
    @Column(length = 40)
    private String slug;                 // matches RewardService.CATALOG

    @Column(name = "file_name", nullable = false, length = 160)
    private String fileName;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, length = 40_000_000)
    @Basic(fetch = FetchType.LAZY)
    private byte[] data;

    @Column(nullable = false) private Long size;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    protected RewardFile() { }
    public RewardFile(String slug) { this.slug = slug; }
    public void replace(String fileName, byte[] data) { this.fileName = fileName; this.data = data; this.size = (long) data.length; this.updatedAt = Instant.now(); }
    public String getSlug() { return slug; }
    public String getFileName() { return fileName; }
    public byte[] getData() { return data; }
    public Long getSize() { return size; }
    public Instant getUpdatedAt() { return updatedAt; }
}
