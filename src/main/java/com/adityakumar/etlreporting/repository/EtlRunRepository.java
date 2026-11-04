package com.adityakumar.etlreporting.repository;

import com.adityakumar.etlreporting.domain.EtlRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EtlRunRepository extends JpaRepository<EtlRun, Long> {
}
