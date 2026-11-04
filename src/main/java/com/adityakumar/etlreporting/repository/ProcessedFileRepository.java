package com.adityakumar.etlreporting.repository;

import com.adityakumar.etlreporting.domain.ProcessedFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedFileRepository extends JpaRepository<ProcessedFile, Long> {

    boolean existsByObjectKeyAndEtag(String objectKey, String etag);
}
