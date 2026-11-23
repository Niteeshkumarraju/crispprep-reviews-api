package com.crispprep.reviews.repository;

import com.crispprep.reviews.entity.ActivityEvent;
import com.crispprep.reviews.entity.ActivityEvent.Type;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ActivityRepository extends JpaRepository<ActivityEvent, Long> {

    long countByType(Type type);

    long countByTypeAndCreatedAtGreaterThanEqual(Type type, Instant since);

    @Query("SELECT COUNT(DISTINCT e.userId) FROM ActivityEvent e WHERE e.userId IS NOT NULL AND e.createdAt >= :since")
    long activeUsersSince(@Param("since") Instant since);

    interface SlugCount { String getSlug(); Long getN(); }

    @Query("""
            SELECT e.pdfSlug AS slug, COUNT(e) AS n FROM ActivityEvent e
            WHERE e.type = :type AND e.createdAt >= :since AND e.pdfSlug IS NOT NULL
            GROUP BY e.pdfSlug ORDER BY COUNT(e) DESC
            """)
    List<SlugCount> topSlugs(@Param("type") Type type, @Param("since") Instant since, Pageable page);

    interface TypeTime { Type getType(); Instant getCreatedAt(); }

    @Query("SELECT e.type AS type, e.createdAt AS createdAt FROM ActivityEvent e WHERE e.createdAt >= :since AND e.type IN :types")
    List<TypeTime> timesSince(@Param("since") Instant since, @Param("types") Collection<Type> types);

    @Query("SELECT e.userId AS userId, COUNT(e) AS n FROM ActivityEvent e WHERE e.type = :type AND e.userId IN :ids GROUP BY e.userId")
    List<UserCount> countPerUser(@Param("type") Type type, @Param("ids") Collection<Long> ids);

    /** Per-user counts for every user at once (no IN list, so it works for any number of users). */
    @Query("SELECT e.userId AS userId, COUNT(e) AS n FROM ActivityEvent e WHERE e.type = :type AND e.userId IS NOT NULL GROUP BY e.userId")
    List<UserCount> countAllPerUser(@Param("type") Type type);

    List<ActivityEvent> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);

    /** The live feed: newest first, paged by id ("load older" passes the last id it has). */
    @Query("SELECT e FROM ActivityEvent e WHERE (:before IS NULL OR e.id < :before) ORDER BY e.id DESC")
    List<ActivityEvent> feed(@Param("before") Long before, Pageable page);

    List<ActivityEvent> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant since);

    interface SlugTypeCount { String getSlug(); Type getType(); Long getN(); }

    @Query("SELECT e.pdfSlug AS slug, e.type AS type, COUNT(e) AS n FROM ActivityEvent e WHERE e.pdfSlug IS NOT NULL AND e.type IN :types GROUP BY e.pdfSlug, e.type")
    List<SlugTypeCount> countBySlugAndType(@Param("types") Collection<Type> types);

    @Modifying
    @Transactional
    @Query("DELETE FROM ActivityEvent e WHERE e.userId = :userId")
    int deleteForUser(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ActivityEvent e WHERE e.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
