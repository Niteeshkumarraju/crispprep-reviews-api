package com.crispprep.reviews.repository;

import com.crispprep.reviews.entity.AdminAction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AdminActionRepository extends JpaRepository<AdminAction, Long> {

    List<AdminAction> findTop20ByTargetUserIdOrderByCreatedAtDesc(Long targetUserId);
}
