package com.adityakumar.etlreporting.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EtlRunTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 10, 0);

    private EtlRun newRun() {
        return new EtlRun(JobName.ORDERS_IMPORT, TriggerType.MANUAL, NOW);
    }

    @Test
    void aRunWithNoFailuresSucceedsEvenWithRejectedRows() {
        EtlRun run = newRun();
        run.addFileResult(10, 8, 0, 2);
        run.finish(NOW.plusMinutes(1));

        assertEquals(RunStatus.SUCCEEDED, run.getStatus());
        assertEquals(2, run.getRowsRejected());
        assertEquals(NOW.plusMinutes(1), run.getFinishedAt());
        assertNull(run.getErrorMessage());
    }

    @Test
    void someFailedFilesAndSomeLoadedFilesMeansPartial() {
        EtlRun run = newRun();
        run.addFileResult(5, 5, 0, 0);
        run.addFileFailure("bad.csv: broken");
        run.finish(NOW);

        assertEquals(RunStatus.PARTIAL, run.getStatus());
    }

    @Test
    void onlyFailedFilesMeansFailed() {
        EtlRun run = newRun();
        run.addFileFailure("bad.csv: broken");
        run.finish(NOW);

        assertEquals(RunStatus.FAILED, run.getStatus());
    }

    @Test
    void anOutrightFailureStaysFailedEvenIfFilesWereLoadedBefore() {
        EtlRun run = newRun();
        run.addFileResult(5, 5, 0, 0);
        run.fail("storage went away");
        run.finish(NOW);

        assertEquals(RunStatus.FAILED, run.getStatus());
        assertTrue(run.getErrorMessage().contains("storage went away"));
    }

    @Test
    void errorMessagesAreJoinedAndTruncatedToFitTheColumn() {
        EtlRun run = newRun();
        run.addFileFailure("a".repeat(800));
        run.addFileFailure("b".repeat(800));

        assertEquals(1000, run.getErrorMessage().length());
        assertTrue(run.getErrorMessage().contains(" | "));
        assertNotNull(run.getJobName());
    }

    @Test
    void aNewRunStartsAsRunning() {
        assertEquals(RunStatus.RUNNING, newRun().getStatus());
    }
}
