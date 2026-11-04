package com.adityakumar.etlreporting;

import com.adityakumar.etlreporting.domain.ExchangeRate;
import com.adityakumar.etlreporting.domain.OrderStatus;
import com.adityakumar.etlreporting.domain.SalesOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportApiTest extends IntegrationTestBase {

    private static final LocalDateTime LOADED = LocalDateTime.of(2026, 3, 5, 0, 0);

    private void order(String id, String category, String amount, String currency, String date, OrderStatus status) {
        orders.save(new SalesOrder(id, "C1", category, new BigDecimal(amount), currency, LocalDate.parse(date),
                status, "test.csv", 1L, LOADED));
    }

    private void rate(String currency, String rate) {
        exchangeRates.save(new ExchangeRate(currency, new BigDecimal(rate), "USD", LocalDate.of(2026, 3, 4), LOADED));
    }

    @BeforeEach
    void loadData() {
        // 1 USD = 1 USD = 100 INR = 0.5 EUR. GBP deliberately has no rate.
        rate("USD", "1");
        rate("INR", "100");
        rate("EUR", "0.5");

        order("R-1", "Books", "1000.00", "INR", "2026-03-01", OrderStatus.COMPLETED);   // 10.00 USD
        order("R-2", "Books", "5.50", "USD", "2026-03-01", OrderStatus.COMPLETED);      // 5.50 USD
        order("R-3", "Home", "10.00", "EUR", "2026-03-01", OrderStatus.COMPLETED);      // 20.00 USD
        order("R-4", "Home", "999.00", "USD", "2026-03-01", OrderStatus.CANCELLED);     // ignored
        order("R-5", "Home", "999.00", "USD", "2026-03-01", OrderStatus.REFUNDED);      // ignored
        order("R-6", "Books", "10.00", "GBP", "2026-03-02", OrderStatus.COMPLETED);     // no rate
        order("R-7", "Home", "500.00", "INR", "2026-03-02", OrderStatus.COMPLETED);     // 5.00 USD
        order("R-8", "Home", "100.00", "USD", "2026-04-01", OrderStatus.COMPLETED);     // outside the range
    }

    @Test
    void dailySalesConvertsToTheBaseCurrencyAndFillsEmptyDays() throws Exception {
        mvc.perform(get("/api/reports/daily-sales?from=2026-03-01&to=2026-03-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.ratesAsOf").value("2026-03-04"))
                .andExpect(jsonPath("$.totalOrders").value(5))
                .andExpect(jsonPath("$.total").value(40.5))
                .andExpect(jsonPath("$.ordersWithoutRate").value(1))
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.days[0].date").value("2026-03-01"))
                .andExpect(jsonPath("$.days[0].orders").value(3))
                .andExpect(jsonPath("$.days[0].total").value(35.5))
                .andExpect(jsonPath("$.days[0].byCurrency.INR").value(1000.0))
                .andExpect(jsonPath("$.days[0].byCurrency.EUR").value(10.0))
                .andExpect(jsonPath("$.days[1].orders").value(2))
                .andExpect(jsonPath("$.days[1].total").value(5.0))
                .andExpect(jsonPath("$.days[1].byCurrency.GBP").value(10.0))
                .andExpect(jsonPath("$.days[2].orders").value(0))
                .andExpect(jsonPath("$.days[2].total").value(0));
    }

    @Test
    void salesByCategoryIsSortedByValueAndReportsOrdersWithoutARate() throws Exception {
        mvc.perform(get("/api/reports/by-category?from=2026-03-01&to=2026-03-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordersWithoutRate").value(1))
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[0].category").value("Home"))
                .andExpect(jsonPath("$.categories[0].orders").value(2))
                .andExpect(jsonPath("$.categories[0].total").value(25.0))
                .andExpect(jsonPath("$.categories[1].category").value("Books"))
                .andExpect(jsonPath("$.categories[1].orders").value(3))
                .andExpect(jsonPath("$.categories[1].total").value(15.5));
    }

    @Test
    void theDefaultRangeIsTheLast30Days() throws Exception {
        mvc.perform(get("/api/reports/daily-sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(30));
    }

    @Test
    void badRangesAndDatesAreRejected() throws Exception {
        mvc.perform(get("/api/reports/daily-sales?from=2026-03-05&to=2026-03-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/daily-sales?from=2025-01-01&to=2026-03-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/daily-sales?from=not-a-date")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/by-category?from=2026-03-05&to=2026-03-01")).andExpect(status().isBadRequest());
    }

    @Test
    void anEmptyDatabaseGivesZerosNotErrors() throws Exception {
        orders.deleteAll();
        exchangeRates.deleteAll();
        mvc.perform(get("/api/reports/daily-sales?from=2026-03-01&to=2026-03-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(0))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.ratesAsOf").doesNotExist());
    }
}
