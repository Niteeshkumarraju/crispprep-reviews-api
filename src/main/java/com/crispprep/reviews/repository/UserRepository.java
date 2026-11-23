package com.crispprep.reviews.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

import com.crispprep.reviews.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    long countByCreatedAtGreaterThanEqual(Instant since);

    long countByGoogleIdIsNotNull();

    long countByPasswordHashIsNotNull();

    List<User> findByCreatedAtGreaterThanEqual(Instant since);

    /** Admin users list: search by name or email (empty q = everyone), newest first via the Pageable. */
    @Query("SELECT u FROM User u WHERE :q = '' OR LOWER(u.email) LIKE CONCAT('%', :q, '%') OR LOWER(u.name) LIKE CONCAT('%', :q, '%')")
    Page<User> search(@Param("q") String q, Pageable page);

    /** Accounts linked to Google keep no password (closes the pre-account-takeover gap). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE User u SET u.passwordHash = NULL WHERE u.googleId IS NOT NULL AND u.passwordHash IS NOT NULL")
    int clearPasswordsOfGoogleAccounts();
}
