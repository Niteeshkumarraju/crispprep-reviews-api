package com.crispprep.reviews.repository;

import com.crispprep.reviews.entity.RewardGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RewardGrantRepository extends JpaRepository<RewardGrant, Long> {
    List<RewardGrant> findByUserId(Long userId);
    boolean existsByUserIdAndSlug(Long userId, String slug);
    long countBySlug(String slug);
}
