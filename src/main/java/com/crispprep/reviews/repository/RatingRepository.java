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

    @Query(value = """
            SELECT p.id AS pdfId,
                   p.title AS title,
                   CAST(ROUND(AVG(r.stars), 2) AS DOUBLE) AS average,
                   COUNT(r.id) AS ratings
            FROM pdf p
            JOIN rating r ON r.pdf_id = p.id
            GROUP BY p.id, p.title
            ORDER BY average DESC, ratings DESC
            LIMIT 5
            """, nativeQuery = true)
    List<TopPdf> findTopRated();
}
