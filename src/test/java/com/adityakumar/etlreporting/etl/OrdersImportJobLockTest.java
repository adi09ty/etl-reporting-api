package com.adityakumar.etlreporting.etl;

import com.adityakumar.etlreporting.alert.AlertService;
import com.adityakumar.etlreporting.domain.EtlRun;
import com.adityakumar.etlreporting.domain.RunStatus;
import com.adityakumar.etlreporting.domain.TriggerType;
import com.adityakumar.etlreporting.exception.JobAlreadyRunningException;
import com.adityakumar.etlreporting.repository.EtlRunRepository;
import com.adityakumar.etlreporting.repository.ProcessedFileRepository;
import com.adityakumar.etlreporting.storage.ObjectStorage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Two runs of the same job must never overlap: the second is refused while the first is still going. */
class OrdersImportJobLockTest {

    @Test
    void aSecondRunIsRefusedWhileTheFirstIsStillRunning() throws Exception {
        CountDownLatch firstRunStarted = new CountDownLatch(1);
        CountDownLatch letFirstRunFinish = new CountDownLatch(1);

        ObjectStorage storage = mock(ObjectStorage.class);
        when(storage.list("incoming/")).thenAnswer(invocation -> {
            firstRunStarted.countDown();
            letFirstRunFinish.await(10, TimeUnit.SECONDS);
            return List.of();
        });
        EtlRunRepository runs = mock(EtlRunRepository.class);
        when(runs.save(any(EtlRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrdersImportJob job = new OrdersImportJob(storage, mock(OrderFileImporter.class), runs,
                mock(ProcessedFileRepository.class), new RetryExecutor(1, Duration.ZERO, d -> { }),
                mock(AlertService.class), Clock.systemUTC());

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<EtlRun> first = pool.submit(() -> job.run(TriggerType.SCHEDULED));
            assertEquals(true, firstRunStarted.await(10, TimeUnit.SECONDS));

            assertThrows(JobAlreadyRunningException.class, () -> job.run(TriggerType.MANUAL));

            letFirstRunFinish.countDown();
            assertEquals(RunStatus.SUCCEEDED, first.get(10, TimeUnit.SECONDS).getStatus());

            // Once the first run is over, the job can run again.
            assertEquals(RunStatus.SUCCEEDED, job.run(TriggerType.MANUAL).getStatus());
        } finally {
            letFirstRunFinish.countDown();
            pool.shutdownNow();
        }
    }
}
