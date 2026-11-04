package com.adityakumar.etlreporting.repository;

import com.adityakumar.etlreporting.domain.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, String> {
}
