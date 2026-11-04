package com.adityakumar.etlreporting.etl;

import com.adityakumar.etlreporting.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderRowValidatorTest {

    private final OrderRowValidator validator =
            new OrderRowValidator(Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC));

    private static Map<String, String> row(String... overrides) {
        Map<String, String> row = new HashMap<>(Map.of(
                "order_id", "ORD-1", "customer_id", "C1", "category", "Books", "amount", "99.50",
                "currency", "INR", "order_date", "2026-03-01", "status", "COMPLETED"));
        for (int i = 0; i < overrides.length; i += 2) {
            row.put(overrides[i], overrides[i + 1]);
        }
        return row;
    }

    @Test
    void aGoodRowBecomesACleanOrder() {
        OrderRowValidator.Outcome outcome = validator.validate(row());

        assertTrue(outcome.isValid());
        assertEquals("ORD-1", outcome.order().orderId());
        assertEquals(new BigDecimal("99.50"), outcome.order().amount());
        assertEquals(LocalDate.of(2026, 3, 1), outcome.order().orderDate());
        assertEquals(OrderStatus.COMPLETED, outcome.order().status());
    }

    @Test
    void valuesAreTrimmedAndAmountsAreNormalisedToTwoDecimals() {
        OrderRowValidator.Outcome outcome = validator.validate(row("amount", " 10 ", "order_id", "  ORD-2 "));

        assertTrue(outcome.isValid());
        assertEquals("ORD-2", outcome.order().orderId());
        assertEquals(new BigDecimal("10.00"), outcome.order().amount());
    }

    @Test
    void todayIsAllowedButTomorrowIsNot() {
        assertTrue(validator.validate(row("order_date", "2026-03-10")).isValid());
        assertFalse(validator.validate(row("order_date", "2026-03-11")).isValid());
    }

    @Test
    void trailingZerosBeyondTwoDecimalsAreHarmless() {
        assertTrue(validator.validate(row("amount", "5.500")).isValid());
    }

    @ParameterizedTest(name = "{0} = ''{1}'' is rejected: {2}")
    @CsvSource(delimiter = '|', value = {
            "order_id|          |order_id is required",
            "order_id|BAD ID!   |order_id must be 1-64 letters, digits, '-' or '_'",
            "customer_id|       |customer_id is required",
            "category|          |category is required",
            "amount|abc         |amount is not a number",
            "amount|            |amount is not a number",
            "amount|0           |amount must be greater than zero",
            "amount|-5          |amount must be greater than zero",
            "amount|1.234       |amount has more than 2 decimal places",
            "amount|1000000001  |amount is larger than 1000000000",
            "currency|XXZ       |currency is not a valid ISO 4217 code",
            "currency|inr       |currency is not a valid ISO 4217 code",
            "currency|INRR      |currency is not a valid ISO 4217 code",
            "order_date|01/03/2026|order_date must be a date like 2026-03-01",
            "order_date|2099-01-01|order_date is in the future",
            "status|SHIPPED     |status must be COMPLETED, CANCELLED or REFUNDED",
            "status|completed   |status must be COMPLETED, CANCELLED or REFUNDED"})
    void invalidRowsAreRejectedWithAClearReason(String column, String value, String expectedReason) {
        // @CsvSource turns a blank cell into null, which here stands for an empty value
        OrderRowValidator.Outcome outcome = validator.validate(row(column, value == null ? "" : value.trim()));

        assertFalse(outcome.isValid());
        assertEquals(expectedReason.trim(), outcome.error());
    }

    @Test
    void aMissingColumnIsTreatedAsAMissingValue() {
        Map<String, String> row = row();
        row.remove("customer_id");

        assertEquals("customer_id is required", validator.validate(row).error());
    }
}
