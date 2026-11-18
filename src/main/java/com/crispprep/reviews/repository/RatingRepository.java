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

    @Query("SELECT AVG(r.stars) FROM Rating r WHERE r.pdf.id = :pdfId")
    Double averageForPdf(@Param("pdfId") Long pdfId);

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
