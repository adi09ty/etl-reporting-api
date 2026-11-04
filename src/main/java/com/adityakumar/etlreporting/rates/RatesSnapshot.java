package com.adityakumar.etlreporting.rates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Exchange rates as published by the source: 1 unit of {@code base} equals {@code rates[CCY]} units of CCY. */
public record RatesSnapshot(String base, LocalDate date, Map<String, BigDecimal> rates) {
}
