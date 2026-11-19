package com.crispprep.reviews.repository;

import com.crispprep.reviews.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByPdfIdAndApprovedTrueOrderByCreatedAtDesc(Long pdfId);

    List<Review> findByApprovedFalseOrderByCreatedAtAsc();

    boolean existsByPdfIdAndUserId(Long pdfId, Long userId);

    long countByUserId(Long userId);

    long countByApprovedFalse();

    java.util.Optional<Review> findByPdfIdAndUserId(Long pdfId, Long userId);

    List<Review> findByUserIdOrderByCreatedAtDesc(Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM Review r WHERE r.user.id = :userId")
    int deleteForUser(@org.springframework.data.repository.query.Param("userId") Long userId);
}
