package com.adityakumar.etlreporting.etl;

import com.adityakumar.etlreporting.config.AppProperties;
import com.adityakumar.etlreporting.domain.ProcessedFile;
import com.adityakumar.etlreporting.domain.RejectedRow;
import com.adityakumar.etlreporting.domain.SalesOrder;
import com.adityakumar.etlreporting.etl.OrderRowValidator.ValidOrder;
import com.adityakumar.etlreporting.exception.InvalidFileException;
import com.adityakumar.etlreporting.repository.ProcessedFileRepository;
import com.adityakumar.etlreporting.repository.RejectedRowRepository;
import com.adityakumar.etlreporting.repository.SalesOrderRepository;
import com.adityakumar.etlreporting.storage.StoredObject;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.ByteArrayInputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads one CSV file. Everything about a file (its orders, its rejected rows and the record that it was
 * processed) is saved in ONE transaction, so a failure leaves nothing half-loaded.
 */
@Service
public class OrderFileImporter {

    public record FileResult(int rowsRead, int inserted, int updated, int rejected) {
    }

    private static final List<String> REQUIRED_COLUMNS = List.of(
            OrderRowValidator.ORDER_ID, OrderRowValidator.CUSTOMER_ID, OrderRowValidator.CATEGORY,
            OrderRowValidator.AMOUNT, OrderRowValidator.CURRENCY, OrderRowValidator.ORDER_DATE,
            OrderRowValidator.STATUS);

    private final OrderRowValidator validator;
    private final SalesOrderRepository orders;
    private final RejectedRowRepository rejectedRows;
    private final ProcessedFileRepository processedFiles;
    private final AppProperties properties;
    private final Clock clock;

    public OrderFileImporter(OrderRowValidator validator, SalesOrderRepository orders,
                             RejectedRowRepository rejectedRows, ProcessedFileRepository processedFiles,
                             AppProperties properties, Clock clock) {
        this.validator = validator;
        this.orders = orders;
        this.rejectedRows = rejectedRows;
        this.processedFiles = processedFiles;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public FileResult importFile(Long runId, StoredObject file, byte[] content) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<ValidOrder> valid = new ArrayList<>();
        List<RejectedRow> rejected = new ArrayList<>();
        int rowsRead = 0;

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            checkHeader(parser.getHeaderMap().keySet());
            Set<String> seenIds = new HashSet<>();
            for (CSVRecord record : parser) {
                rowsRead++;
                OrderRowValidator.Outcome outcome = validator.validate(record.toMap());
                if (!outcome.isValid()) {
                    rejected.add(rejection(runId, file, record, outcome.error(), now));
                } else if (!seenIds.add(outcome.order().orderId())) {
                    rejected.add(rejection(runId, file, record, "duplicate order_id in this file", now));
                } else {
                    valid.add(outcome.order());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not parse " + file.key(), e);
        } catch (IllegalStateException e) {
            // commons-csv signals a malformed file (for example an unclosed quote) this way
            throw new InvalidFileException("Malformed CSV in " + file.key() + ": " + e.getMessage());
        }

        int[] counts = upsert(valid, runId, file.key(), now);
        rejectedRows.saveAll(rejected);
        processedFiles.save(new ProcessedFile(file.key(), file.etag(), runId, now));
        return new FileResult(rowsRead, counts[0], counts[1], rejected.size());
    }

    private static void checkHeader(Set<String> header) {
        List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !header.contains(c)).toList();
        if (!missing.isEmpty()) {
            throw new InvalidFileException("Missing required columns: " + String.join(", ", missing));
        }
    }

    /** Inserts new orders and updates existing ones (matched by order_id), in chunks. Returns [inserted, updated]. */
    private int[] upsert(List<ValidOrder> valid, Long runId, String sourceFile, LocalDateTime now) {
        int inserted = 0;
        int updated = 0;
        int chunkSize = Math.max(1, properties.etl().chunkSize());
        for (int from = 0; from < valid.size(); from += chunkSize) {
            List<ValidOrder> chunk = valid.subList(from, Math.min(from + chunkSize, valid.size()));
            Map<String, SalesOrder> existing = orders
                    .findByOrderIdIn(chunk.stream().map(ValidOrder::orderId).toList()).stream()
                    .collect(Collectors.toMap(SalesOrder::getOrderId, Function.identity()));
            List<SalesOrder> toInsert = new ArrayList<>();
            for (ValidOrder order : chunk) {
                SalesOrder current = existing.get(order.orderId());
                if (current == null) {
                    toInsert.add(new SalesOrder(order.orderId(), order.customerId(), order.category(),
                            order.amount(), order.currency(), order.orderDate(), order.status(), sourceFile,
                            runId, now));
                } else {
                    current.update(order.customerId(), order.category(), order.amount(), order.currency(),
                            order.orderDate(), order.status(), sourceFile, runId, now);
                    updated++;
                }
            }
            orders.saveAll(toInsert);
            inserted += toInsert.size();
        }
        return new int[]{inserted, updated};
    }

    private static RejectedRow rejection(Long runId, StoredObject file, CSVRecord record, String reason,
                                         LocalDateTime now) {
        return new RejectedRow(runId, file.key(), record.getRecordNumber(), String.join(",", record.values()),
                reason, now);
    }
}
