package com.adityakumar.etlreporting.rates;

import com.adityakumar.etlreporting.domain.ExchangeRate;
import com.adityakumar.etlreporting.repository.ExchangeRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Saves a snapshot of rates in one transaction: either every rate is updated or none is. */
@Service
public class RatesWriter {

    public record Result(int inserted, int updated) {
    }

    private final ExchangeRateRepository rates;
    private final Clock clock;

    public RatesWriter(ExchangeRateRepository rates, Clock clock) {
        this.rates = rates;
        this.clock = clock;
    }

    @Transactional
    public Result save(RatesSnapshot snapshot) {
        // The base currency is always worth exactly 1 of itself; the API leaves it out of the list.
        Map<String, BigDecimal> all = new LinkedHashMap<>(snapshot.rates());
        all.put(snapshot.base(), BigDecimal.ONE);

        Map<String, ExchangeRate> existing = rates.findAllById(all.keySet()).stream()
                .collect(Collectors.toMap(ExchangeRate::getCurrency, Function.identity()));
        LocalDateTime now = LocalDateTime.now(clock);
        List<ExchangeRate> toInsert = new ArrayList<>();
        int updated = 0;
        for (Map.Entry<String, BigDecimal> entry : all.entrySet()) {
            ExchangeRate current = existing.get(entry.getKey());
            if (current == null) {
                toInsert.add(new ExchangeRate(entry.getKey(), entry.getValue(), snapshot.base(), snapshot.date(), now));
            } else {
                current.update(entry.getValue(), snapshot.base(), snapshot.date(), now);
                updated++;
            }
        }
        rates.saveAll(toInsert);
        return new Result(toInsert.size(), updated);
    }
}
