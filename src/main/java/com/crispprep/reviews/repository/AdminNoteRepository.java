package com.crispprep.reviews.repository;

import com.crispprep.reviews.entity.AdminNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface AdminNoteRepository extends JpaRepository<AdminNote, Long> {

    List<AdminNote> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM AdminNote n WHERE n.userId = :userId")
    int deleteForUser(@Param("userId") Long userId);
}
