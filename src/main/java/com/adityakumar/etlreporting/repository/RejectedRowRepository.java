package com.adityakumar.etlreporting.repository;

import com.adityakumar.etlreporting.domain.RejectedRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RejectedRowRepository extends JpaRepository<RejectedRow, Long> {

    Page<RejectedRow> findByRunIdOrderByIdAsc(Long runId, Pageable pageable);
}
