package com.adityakumar.etlreporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Units of this currency per 1 unit of the base currency (for USD base: 1 USD = rate units). */
@Entity
@Table(name = "exchange_rates")
public class ExchangeRate {

    @Id
    @Column(length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal rate;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    protected ExchangeRate() {
        // required by JPA
    }

    public ExchangeRate(String currency, BigDecimal rate, String baseCurrency, LocalDate rateDate,
                        LocalDateTime fetchedAt) {
        this.currency = currency;
        update(rate, baseCurrency, rateDate, fetchedAt);
    }

    public final void update(BigDecimal rate, String baseCurrency, LocalDate rateDate, LocalDateTime fetchedAt) {
        this.rate = rate;
        this.baseCurrency = baseCurrency;
        this.rateDate = rateDate;
        this.fetchedAt = fetchedAt;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public LocalDate getRateDate() {
        return rateDate;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }
}
