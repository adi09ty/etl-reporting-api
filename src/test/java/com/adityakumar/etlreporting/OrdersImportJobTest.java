package com.adityakumar.etlreporting;

import com.adityakumar.etlreporting.domain.EtlRun;
import com.adityakumar.etlreporting.domain.RejectedRow;
import com.adityakumar.etlreporting.domain.RunStatus;
import com.adityakumar.etlreporting.domain.SalesOrder;
import com.adityakumar.etlreporting.domain.TriggerType;
import com.adityakumar.etlreporting.etl.OrdersImportJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrdersImportJobTest extends IntegrationTestBase {

    private static final String HEADER = "order_id,customer_id,category,amount,currency,order_date,status\n";

    @Autowired OrdersImportJob job;

    @Test
    void loadsValidRowsRejectsInvalidOnesAndMovesTheFile() throws IOException {
        putFile("incoming/orders-a.csv", HEADER
                + "A-1,C1,Books,100.00,INR,2026-03-01,COMPLETED\n"
                + "A-2,C2,Home,25.50,USD,2026-03-01,COMPLETED\n"
                + "A-3,C3,Books,40,EUR,2026-03-02,REFUNDED\n"
                + "A-4,C4,Books,-5,INR,2026-03-02,COMPLETED\n"
                + "A-1,C1,Books,999.00,INR,2026-03-01,COMPLETED\n");

        EtlRun run = job.run(TriggerType.MANUAL);

        assertEquals(RunStatus.SUCCEEDED, run.getStatus());
        assertEquals(1, run.getFilesProcessed());
        assertEquals(5, run.getRowsRead());
        assertEquals(3, run.getRowsInserted());
        assertEquals(0, run.getRowsUpdated());
        assertEquals(2, run.getRowsRejected());
        assertTrue(alerts.subjects.isEmpty(), "rejected rows are a data-quality matter, not a job failure");

        assertEquals(3, orders.count());
        SalesOrder first = orders.findByOrderIdIn(List.of("A-1")).get(0);
        assertEquals(0, new BigDecimal("100.00").compareTo(first.getAmount()), "the first A-1 row wins, the duplicate is rejected");

        List<RejectedRow> rejected = rejectedRows.findByRunIdOrderByIdAsc(run.getId(),
                org.springframework.data.domain.Pageable.unpaged()).getContent();
        assertEquals(2, rejected.size());
        assertEquals("amount must be greater than zero", rejected.get(0).getReason());
        assertEquals(4, rejected.get(0).getRowNumber());
        assertEquals("duplicate order_id in this file", rejected.get(1).getReason());

        assertFalse(fileExists("incoming/orders-a.csv"));
        assertEquals(1, filesUnder("processed").size());
        assertTrue(filesUnder("processed").get(0).endsWith("orders-a.csv"));
    }

    @Test
    void aLaterFileWithTheSameOrderIdsUpdatesInsteadOfDuplicating() {
        putFile("incoming/day1.csv", HEADER + "B-1,C1,Books,10.00,USD,2026-03-01,COMPLETED\n");
        job.run(TriggerType.MANUAL);

        putFile("incoming/day1-corrected.csv", HEADER + "B-1,C1,Books,12.00,USD,2026-03-01,COMPLETED\n");
        EtlRun second = job.run(TriggerType.MANUAL);

        assertEquals(0, second.getRowsInserted());
        assertEquals(1, second.getRowsUpdated());
        assertEquals(1, orders.count());
        assertEquals(0, new BigDecimal("12.00").compareTo(orders.findByOrderIdIn(List.of("B-1")).get(0).getAmount()));
    }

    @Test
    void aBrokenFileDoesNotStopTheOthersAndTriggersAnAlert() throws IOException {
        putFile("incoming/a-bad.csv", "id,amount\n1,5\n");
        putFile("incoming/b-good.csv", HEADER + "C-1,C1,Books,10.00,USD,2026-03-01,COMPLETED\n");

        EtlRun run = job.run(TriggerType.SCHEDULED);

        assertEquals(RunStatus.PARTIAL, run.getStatus());
        assertEquals(1, run.getFilesProcessed());
        assertEquals(1, run.getFilesFailed());
        assertTrue(run.getErrorMessage().contains("Missing required columns"));
        assertEquals(1, orders.count());
        assertEquals(1, filesUnder("failed").size());
        assertEquals(1, filesUnder("processed").size());
        assertEquals(1, alerts.subjects.size());
        assertTrue(alerts.subjects.get(0).contains("PARTIAL"));
    }

    @Test
    void whenNothingCanBeLoadedTheRunFailsAndAlerts() {
        putFile("incoming/bad.csv", "just,some,text\n1,2,3\n");

        EtlRun run = job.run(TriggerType.MANUAL);

        assertEquals(RunStatus.FAILED, run.getStatus());
        assertEquals(0, orders.count());
        assertEquals(1, alerts.subjects.size());
        assertTrue(alerts.subjects.get(0).contains("FAILED"));
    }

    @Test
    void aFileThatWasAlreadyLoadedIsSkippedAndOnlyMoved() throws IOException {
        String content = HEADER + "D-1,C1,Books,10.00,USD,2026-03-01,COMPLETED\n";
        putFile("incoming/same.csv", content);
        job.run(TriggerType.MANUAL);

        // The same file reappears, as if moving it had failed the first time.
        putFile("incoming/same.csv", content);
        EtlRun second = job.run(TriggerType.MANUAL);

        assertEquals(RunStatus.SUCCEEDED, second.getStatus());
        assertEquals(0, second.getFilesProcessed());
        assertEquals(0, second.getRowsRead());
        assertEquals(1, orders.count());
        assertFalse(fileExists("incoming/same.csv"));
        assertEquals(2, filesUnder("processed").size());
    }

    @Test
    void emptyInboxAndNonCsvFilesAreIgnored() {
        putFile("incoming/readme.txt", "not data");

        EtlRun run = job.run(TriggerType.MANUAL);

        assertEquals(RunStatus.SUCCEEDED, run.getStatus());
        assertEquals(0, run.getFilesProcessed());
        assertTrue(fileExists("incoming/readme.txt"));
    }

    @Test
    void aHeaderOnlyFileLoadsNothingButCountsAsProcessed() {
        putFile("incoming/empty.csv", HEADER);

        EtlRun run = job.run(TriggerType.MANUAL);

        assertEquals(RunStatus.SUCCEEDED, run.getStatus());
        assertEquals(1, run.getFilesProcessed());
        assertEquals(0, run.getRowsRead());
    }

    @Test
    void quotedFieldsAndWindowsLineEndingsAreParsedCorrectly() {
        putFile("incoming/quoted.csv", HEADER.replace("\n", "\r\n")
                + "E-1,C1,\"Home, Garden\",10.00,USD,2026-03-01,COMPLETED\r\n");

        EtlRun run = job.run(TriggerType.MANUAL);

        assertEquals(1, run.getRowsInserted());
        assertEquals("Home, Garden", orders.findByOrderIdIn(List.of("E-1")).get(0).getCategory());
    }
}
