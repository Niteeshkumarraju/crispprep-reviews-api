package com.crispprep.reviews.repository;

import com.crispprep.reviews.dto.TopPdf;
import com.crispprep.reviews.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    boolean existsByPdfIdAndUserId(Long pdfId, Long userId);

    Optional<Rating> findByPdfIdAndUserId(Long pdfId, Long userId);

    long countByPdfId(Long pdfId);

    long countByUserId(Long userId);

    @Query("SELECT AVG(r.stars) FROM Rating r")
    Double averageAll();

    @Query("SELECT r.user.id AS userId, COUNT(r) AS n FROM Rating r WHERE r.user.id IN :ids GROUP BY r.user.id")
    List<UserCount> countPerUser(@Param("ids") java.util.Collection<Long> ids);

    @Query("SELECT AVG(r.stars) FROM Rating r WHERE r.pdf.id = :pdfId")
    Double averageForPdf(@Param("pdfId") Long pdfId);

    /** Every rated PDF's average and count, keyed by the website's slug (for the stars on the PDF cards). */
    @Query("SELECT p.slug AS slug, AVG(r.stars) AS average, COUNT(r.id) AS ratings FROM Rating r JOIN r.pdf p WHERE p.slug IS NOT NULL GROUP BY p.slug")
    List<SlugRating> allBySlug();

    interface SlugRating { String getSlug(); Double getAverage(); Long getRatings(); }

    /** Top 5 PDFs by average stars (JPQL, so it runs on both MySQL and PostgreSQL). */
    @Query("""
            SELECT p.id AS pdfId, p.title AS title, AVG(r.stars) AS average, COUNT(r.id) AS ratings
            FROM Rating r JOIN r.pdf p
            GROUP BY p.id, p.title
            ORDER BY AVG(r.stars) DESC, COUNT(r.id) DESC
            LIMIT 5
            """)
    List<TopPdf> findTopRated();
}
