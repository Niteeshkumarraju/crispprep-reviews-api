package com.crispprep.reviews.repository;

import com.crispprep.reviews.entity.RewardFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface RewardFileRepository extends JpaRepository<RewardFile, String> {
    interface Info { String getSlug(); String getFileName(); Long getSize(); java.time.Instant getUpdatedAt(); }
    @Query("SELECT f.slug AS slug, f.fileName AS fileName, f.size AS size, f.updatedAt AS updatedAt FROM RewardFile f")
    List<Info> listInfo();   // without loading the PDF bytes
}
