package com.crispprep.reviews.repository;

import com.crispprep.reviews.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    Optional<QuizAttempt> findByUserIdAndDay(Long userId, LocalDate day);
    long countByDay(LocalDate day);
    long countByUserId(Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM QuizAttempt q WHERE q.userId = :userId")
    int deleteForUser(@org.springframework.data.repository.query.Param("userId") Long userId);
}
