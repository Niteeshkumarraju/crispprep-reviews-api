package com.crispprep.reviews.repository;

import com.crispprep.reviews.entity.UserProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserProgressRepository extends JpaRepository<UserProgress, Long> {
    Optional<UserProgress> findByRefCode(String refCode);
    long countByReferredByAndReferralQualifiedAtIsNotNull(Long referredBy);
    List<UserProgress> findByReferredBy(Long referredBy);

    interface Top { Long getUserId(); Long getN(); }
    @Query("SELECT p.referredBy AS userId, COUNT(p) AS n FROM UserProgress p WHERE p.referredBy IS NOT NULL AND p.referralQualifiedAt >= :since GROUP BY p.referredBy ORDER BY COUNT(p) DESC")
    List<Top> topReferrers(@Param("since") Instant since, org.springframework.data.domain.Pageable page);
}
