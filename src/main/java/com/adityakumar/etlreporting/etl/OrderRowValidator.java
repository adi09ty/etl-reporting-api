package com.adityakumar.etlreporting.etl;

import com.adityakumar.etlreporting.domain.OrderStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.Map;
import java.util.regex.Pattern;

/** The data-quality rules for one CSV row. Returns either a clean order or the first reason it is invalid. */
@Component
public class OrderRowValidator {

    public static final String ORDER_ID = "order_id";
    public static final String CUSTOMER_ID = "customer_id";
    public static final String CATEGORY = "category";
    public static final String AMOUNT = "amount";
    public static final String CURRENCY = "currency";
    public static final String ORDER_DATE = "order_date";
    public static final String STATUS = "status";

    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");

    public record ValidOrder(String orderId, String customerId, String category, BigDecimal amount,
                             String currency, LocalDate orderDate, OrderStatus status) {
    }

    public record Outcome(ValidOrder order, String error) {
        public boolean isValid() {
            return order != null;
        }
    }

    private final Clock clock;

    public OrderRowValidator(Clock clock) {
        this.clock = clock;
    }

    public Outcome validate(Map<String, String> row) {
        String orderId = value(row, ORDER_ID);
        if (orderId.isEmpty()) {
            return reject("order_id is required");
        }
        if (orderId.length() > 64 || !ID_PATTERN.matcher(orderId).matches()) {
            return reject("order_id must be 1-64 letters, digits, '-' or '_'");
        }

        String customerId = value(row, CUSTOMER_ID);
        if (customerId.isEmpty()) {
            return reject("customer_id is required");
        }
        if (customerId.length() > 64) {
            return reject("customer_id is longer than 64 characters");
        }

        String category = value(row, CATEGORY);
        if (category.isEmpty()) {
            return reject("category is required");
        }
        if (category.length() > 50) {
            return reject("category is longer than 50 characters");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(value(row, AMOUNT));
        } catch (NumberFormatException e) {
            return reject("amount is not a number");
        }
        if (amount.signum() <= 0) {
            return reject("amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            return reject("amount has more than 2 decimal places");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            return reject("amount is larger than 1000000000");
        }

        String currency = value(row, CURRENCY);
        if (!CURRENCY_PATTERN.matcher(currency).matches() || !isKnownCurrency(currency)) {
            return reject("currency is not a valid ISO 4217 code");
        }

        LocalDate orderDate;
        try {
            orderDate = LocalDate.parse(value(row, ORDER_DATE));
        } catch (DateTimeParseException e) {
            return reject("order_date must be a date like 2026-03-01");
        }
        if (orderDate.isAfter(LocalDate.now(clock))) {
            return reject("order_date is in the future");
        }

        OrderStatus status;
        try {
            status = OrderStatus.valueOf(value(row, STATUS));
        } catch (IllegalArgumentException e) {
            return reject("status must be COMPLETED, CANCELLED or REFUNDED");
        }

        return new Outcome(new ValidOrder(orderId, customerId, category, amount.setScale(2), currency,
                orderDate, status), null);
    }

    private static boolean isKnownCurrency(String code) {
        try {
            Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String value(Map<String, String> row, String column) {
        String raw = row.get(column);
        return raw == null ? "" : raw.trim();
    }

    private static Outcome reject(String reason) {
        return new Outcome(null, reason);
    }
}
