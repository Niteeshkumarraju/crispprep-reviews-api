package com.crispprep.reviews.repository;

import com.crispprep.reviews.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    Optional<QuizAttempt> findByUserIdAndDay(Long userId, LocalDate day);
    long countByDay(LocalDate day);
    long countByUserId(Long userId);
}
