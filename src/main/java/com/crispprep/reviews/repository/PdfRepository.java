package com.crispprep.reviews.repository;

import com.crispprep.reviews.entity.Pdf;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PdfRepository extends JpaRepository<Pdf, Long> {

    Optional<Pdf> findBySlug(String slug);

    java.util.List<Pdf> findByLaunchedAtIsNotNull();
}
