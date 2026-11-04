package com.adityakumar.etlreporting.repository;

import com.adityakumar.etlreporting.domain.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    List<SalesOrder> findByOrderIdIn(Collection<String> orderIds);

    /**
     * Rows of [order date, currency, order count, amount sum] for completed orders in the date range.
     * The database does the grouping; conversion to USD happens afterwards because it needs the rates.
     */
    @Query("""
            select o.orderDate, o.currency, count(o), sum(o.amount)
            from SalesOrder o
            where o.status = com.adityakumar.etlreporting.domain.OrderStatus.COMPLETED
              and o.orderDate between :from and :to
            group by o.orderDate, o.currency
            order by o.orderDate
            """)
    List<Object[]> dailyTotals(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Rows of [category, currency, order count, amount sum] for completed orders in the date range. */
    @Query("""
            select o.category, o.currency, count(o), sum(o.amount)
            from SalesOrder o
            where o.status = com.adityakumar.etlreporting.domain.OrderStatus.COMPLETED
              and o.orderDate between :from and :to
            group by o.category, o.currency
            """)
    List<Object[]> categoryTotals(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
