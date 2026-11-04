package com.adityakumar.etlreporting.report;

import com.adityakumar.etlreporting.config.AppProperties;
import com.adityakumar.etlreporting.domain.ExchangeRate;
import com.adityakumar.etlreporting.dto.Dtos.CategoryEntry;
import com.adityakumar.etlreporting.dto.Dtos.CategoryReport;
import com.adityakumar.etlreporting.dto.Dtos.DailySalesReport;
import com.adityakumar.etlreporting.dto.Dtos.DayEntry;
import com.adityakumar.etlreporting.exception.InvalidRequestException;
import com.adityakumar.etlreporting.repository.ExchangeRateRepository;
import com.adityakumar.etlreporting.repository.SalesOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sales reports over the loaded orders. The database groups the orders; the service then converts each group
 * into the base currency with the latest exchange rates. Only COMPLETED orders count.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    static final int MAX_RANGE_DAYS = 366;
    private static final int DEFAULT_RANGE_DAYS = 30;

    private final SalesOrderRepository orders;
    private final ExchangeRateRepository exchangeRates;
    private final AppProperties properties;
    private final Clock clock;

    public ReportService(SalesOrderRepository orders, ExchangeRateRepository exchangeRates,
                         AppProperties properties, Clock clock) {
        this.orders = orders;
        this.exchangeRates = exchangeRates;
        this.properties = properties;
        this.clock = clock;
    }

    public DailySalesReport dailySales(LocalDate fromParam, LocalDate toParam) {
        LocalDate to = toParam != null ? toParam : LocalDate.now(clock);
        LocalDate from = fromParam != null ? fromParam : to.minusDays(DEFAULT_RANGE_DAYS - 1L);
        checkRange(from, to);

        List<ExchangeRate> rateRows = exchangeRates.findAll();
        Map<String, BigDecimal> rates = rateRows.stream()
                .collect(Collectors.toMap(ExchangeRate::getCurrency, ExchangeRate::getRate));

        Map<LocalDate, Day> byDate = new TreeMap<>();
        long ordersWithoutRate = 0;
        for (Object[] row : orders.dailyTotals(from, to)) {
            LocalDate date = (LocalDate) row[0];
            String currency = (String) row[1];
            long count = ((Number) row[2]).longValue();
            BigDecimal sum = (BigDecimal) row[3];

            Day day = byDate.computeIfAbsent(date, d -> new Day());
            day.orders += count;
            day.byCurrency.merge(currency, sum, BigDecimal::add);
            BigDecimal converted = convert(sum, currency, rates);
            if (converted == null) {
                ordersWithoutRate += count;
            } else {
                day.total = day.total.add(converted);
            }
        }

        List<DayEntry> days = new ArrayList<>();
        long totalOrders = 0;
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            Day day = byDate.getOrDefault(date, new Day());
            days.add(new DayEntry(date, day.orders, day.total, new TreeMap<>(day.byCurrency)));
            totalOrders += day.orders;
            total = total.add(day.total);
        }
        return new DailySalesReport(from, to, properties.rates().baseCurrency(), ratesAsOf(rateRows),
                totalOrders, total, ordersWithoutRate, days);
    }

    public CategoryReport byCategory(LocalDate fromParam, LocalDate toParam) {
        LocalDate to = toParam != null ? toParam : LocalDate.now(clock);
        LocalDate from = fromParam != null ? fromParam : to.minusDays(DEFAULT_RANGE_DAYS - 1L);
        checkRange(from, to);

        List<ExchangeRate> rateRows = exchangeRates.findAll();
        Map<String, BigDecimal> rates = rateRows.stream()
                .collect(Collectors.toMap(ExchangeRate::getCurrency, ExchangeRate::getRate));

        Map<String, Day> byCategory = new HashMap<>();
        long ordersWithoutRate = 0;
        for (Object[] row : orders.categoryTotals(from, to)) {
            String category = (String) row[0];
            String currency = (String) row[1];
            long count = ((Number) row[2]).longValue();
            BigDecimal sum = (BigDecimal) row[3];

            Day entry = byCategory.computeIfAbsent(category, c -> new Day());
            entry.orders += count;
            BigDecimal converted = convert(sum, currency, rates);
            if (converted == null) {
                ordersWithoutRate += count;
            } else {
                entry.total = entry.total.add(converted);
            }
        }

        List<CategoryEntry> categories = byCategory.entrySet().stream()
                .map(e -> new CategoryEntry(e.getKey(), e.getValue().orders, e.getValue().total))
                .sorted(Comparator.comparing(CategoryEntry::total).reversed().thenComparing(CategoryEntry::category))
                .toList();
        return new CategoryReport(from, to, properties.rates().baseCurrency(), ratesAsOf(rateRows),
                ordersWithoutRate, categories);
    }

    /** rate = units of the currency per 1 base unit, so base amount = amount / rate. Null when there is no rate. */
    private static BigDecimal convert(BigDecimal amount, String currency, Map<String, BigDecimal> rates) {
        BigDecimal rate = rates.get(currency);
        return rate == null ? null : amount.divide(rate, 2, RoundingMode.HALF_UP);
    }

    private static LocalDate ratesAsOf(List<ExchangeRate> rateRows) {
        return rateRows.stream().map(ExchangeRate::getRateDate).max(Comparator.naturalOrder()).orElse(null);
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new InvalidRequestException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new InvalidRequestException("The date range can be at most " + MAX_RANGE_DAYS + " days");
        }
    }

    /** Mutable accumulator used while grouping rows. */
    private static final class Day {
        long orders;
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        final Map<String, BigDecimal> byCurrency = new HashMap<>();
    }
}
