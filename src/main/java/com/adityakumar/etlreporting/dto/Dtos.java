package com.adityakumar.etlreporting.dto;

import com.adityakumar.etlreporting.domain.EtlRun;
import com.adityakumar.etlreporting.domain.JobName;
import com.adityakumar.etlreporting.domain.RejectedRow;
import com.adityakumar.etlreporting.domain.RunStatus;
import com.adityakumar.etlreporting.domain.TriggerType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Response shapes of the API, grouped in one file. Entities are never returned directly. */
public final class Dtos {

    private Dtos() {
    }

    public record EtlRunResponse(Long id, JobName jobName, TriggerType trigger, RunStatus status,
                                 LocalDateTime startedAt, LocalDateTime finishedAt, int filesProcessed,
                                 int filesFailed, int rowsRead, int rowsInserted, int rowsUpdated,
                                 int rowsRejected, String errorMessage) {
        public static EtlRunResponse from(EtlRun run) {
            return new EtlRunResponse(run.getId(), run.getJobName(), run.getTrigger(), run.getStatus(),
                    run.getStartedAt(), run.getFinishedAt(), run.getFilesProcessed(), run.getFilesFailed(),
                    run.getRowsRead(), run.getRowsInserted(), run.getRowsUpdated(), run.getRowsRejected(),
                    run.getErrorMessage());
        }
    }

    public record RejectedRowResponse(Long id, Long runId, String sourceFile, long rowNumber, String rawData,
                                      String reason) {
        public static RejectedRowResponse from(RejectedRow row) {
            return new RejectedRowResponse(row.getId(), row.getRunId(), row.getSourceFile(), row.getRowNumber(),
                    row.getRawData(), row.getReason());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }

    /**
     * Completed orders per day. {@code total} is converted to {@code currency} (the rates' base currency) with the
     * latest stored rates; orders in a currency without a rate are counted in {@code ordersWithoutRate} and left
     * out of the totals.
     */
    public record DailySalesReport(LocalDate from, LocalDate to, String currency, LocalDate ratesAsOf,
                                   long totalOrders, BigDecimal total, long ordersWithoutRate,
                                   List<DayEntry> days) {
    }

    public record DayEntry(LocalDate date, long orders, BigDecimal total, Map<String, BigDecimal> byCurrency) {
    }

    public record CategoryReport(LocalDate from, LocalDate to, String currency, LocalDate ratesAsOf,
                                 long ordersWithoutRate, List<CategoryEntry> categories) {
    }

    public record CategoryEntry(String category, long orders, BigDecimal total) {
    }
}
