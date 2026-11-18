package com.crispprep.reviews.repository;

import com.crispprep.reviews.entity.Pdf;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PdfRepository extends JpaRepository<Pdf, Long> { }