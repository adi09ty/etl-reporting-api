package com.adityakumar.etlreporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One validated order loaded from a CSV file. order_id is unique, so re-loading the same order updates it. */
@Entity
@Table(name = "orders", indexes = @Index(name = "idx_orders_date", columnList = "order_date"))
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderStatus status;

    @Column(name = "source_file", nullable = false, length = 255)
    private String sourceFile;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "loaded_at", nullable = false)
    private LocalDateTime loadedAt;

    protected SalesOrder() {
        // required by JPA
    }

    public SalesOrder(String orderId, String customerId, String category, BigDecimal amount, String currency,
                      LocalDate orderDate, OrderStatus status, String sourceFile, Long runId, LocalDateTime loadedAt) {
        this.orderId = orderId;
        update(customerId, category, amount, currency, orderDate, status, sourceFile, runId, loadedAt);
    }

    public final void update(String customerId, String category, BigDecimal amount, String currency,
                             LocalDate orderDate, OrderStatus status, String sourceFile, Long runId,
                             LocalDateTime loadedAt) {
        this.customerId = customerId;
        this.category = category;
        this.amount = amount;
        this.currency = currency;
        this.orderDate = orderDate;
        this.status = status;
        this.sourceFile = sourceFile;
        this.runId = runId;
        this.loadedAt = loadedAt;
    }

    public Long getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public Long getRunId() {
        return runId;
    }

    public LocalDateTime getLoadedAt() {
        return loadedAt;
    }
}
