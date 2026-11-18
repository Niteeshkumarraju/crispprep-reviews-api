package com.crispprep.reviews.repository;

import com.crispprep.reviews.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByPdfIdAndApprovedTrueOrderByCreatedAtDesc(Long pdfId);

    List<Review> findByApprovedFalseOrderByCreatedAtAsc();

    boolean existsByPdfIdAndUserId(Long pdfId, Long userId);

    long countByUserId(Long userId);
}
