package com.adityakumar.etlreporting.etl;

import com.adityakumar.etlreporting.alert.AlertService;
import com.adityakumar.etlreporting.domain.EtlRun;
import com.adityakumar.etlreporting.domain.JobName;
import com.adityakumar.etlreporting.domain.RunStatus;
import com.adityakumar.etlreporting.domain.TriggerType;
import com.adityakumar.etlreporting.exception.JobAlreadyRunningException;
import com.adityakumar.etlreporting.repository.EtlRunRepository;
import com.adityakumar.etlreporting.repository.ProcessedFileRepository;
import com.adityakumar.etlreporting.storage.ObjectStorage;
import com.adityakumar.etlreporting.storage.StoredObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Imports every CSV in "incoming/": each file is loaded in its own transaction and then moved to
 * "processed/" or, if it could not be loaded, to "failed/". One bad file never blocks the others.
 *
 * Re-running is safe. The file is moved only AFTER its data is committed, and a file that was already
 * loaded (same key and content) is recognised and only moved. So a crash at any point loses nothing and
 * duplicates nothing.
 */
@Service
public class OrdersImportJob {

    static final String INCOMING = "incoming/";
    static final String PROCESSED = "processed/";
    static final String FAILED = "failed/";

    private static final Logger log = LoggerFactory.getLogger(OrdersImportJob.class);

    private final ObjectStorage storage;
    private final OrderFileImporter importer;
    private final EtlRunRepository runs;
    private final ProcessedFileRepository processedFiles;
    private final RetryExecutor retry;
    private final AlertService alerts;
    private final Clock clock;

    /** Stops two runs of this job overlapping inside one app instance (manual trigger while a schedule runs). */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OrdersImportJob(ObjectStorage storage, OrderFileImporter importer, EtlRunRepository runs,
                           ProcessedFileRepository processedFiles, RetryExecutor retry, AlertService alerts,
                           Clock clock) {
        this.storage = storage;
        this.importer = importer;
        this.runs = runs;
        this.processedFiles = processedFiles;
        this.retry = retry;
        this.alerts = alerts;
        this.clock = clock;
    }

    public EtlRun run(TriggerType trigger) {
        if (!running.compareAndSet(false, true)) {
            throw new JobAlreadyRunningException(JobName.ORDERS_IMPORT);
        }
        EtlRun run = runs.save(new EtlRun(JobName.ORDERS_IMPORT, trigger, now()));
        try {
            List<StoredObject> files = retry.call("listing " + INCOMING, () -> storage.list(INCOMING)).stream()
                    .filter(f -> f.key().toLowerCase(Locale.ROOT).endsWith(".csv"))
                    .toList();
            for (StoredObject file : files) {
                processFile(run, file);
            }
        } catch (RuntimeException e) {
            log.error("Orders import run {} failed", run.getId(), e);
            run.fail("Import failed: " + e.getMessage());
        } finally {
            run.finish(now());
            runs.save(run);
            running.set(false);
        }
        log.info("Orders import run {} finished: status={} files={} failedFiles={} read={} inserted={} updated={} rejected={}",
                run.getId(), run.getStatus(), run.getFilesProcessed(), run.getFilesFailed(), run.getRowsRead(),
                run.getRowsInserted(), run.getRowsUpdated(), run.getRowsRejected());
        if (run.getStatus() == RunStatus.FAILED || run.getStatus() == RunStatus.PARTIAL) {
            alerts.send("ORDERS_IMPORT run " + run.getId() + " " + run.getStatus(), run.getErrorMessage());
        }
        return run;
    }

    private void processFile(EtlRun run, StoredObject file) {
        if (processedFiles.existsByObjectKeyAndEtag(file.key(), file.etag())) {
            log.info("{} was already loaded, only moving it", file.key());
            moveBestEffort(file.key(), destination(PROCESSED, run, file));
            return;
        }
        try {
            byte[] content = retry.call("reading " + file.key(), () -> storage.read(file.key()));
            OrderFileImporter.FileResult result = importer.importFile(run.getId(), file, content);
            run.addFileResult(result.rowsRead(), result.inserted(), result.updated(), result.rejected());
            moveBestEffort(file.key(), destination(PROCESSED, run, file));
        } catch (RuntimeException e) {
            log.warn("Could not import {}: {}", file.key(), e.getMessage());
            run.addFileFailure(file.key() + ": " + e.getMessage());
            moveBestEffort(file.key(), destination(FAILED, run, file));
        }
    }

    /**
     * The data is already safe by now, so a failed move is only logged: the file stays in "incoming/"
     * and the next run recognises it as loaded and moves it again.
     */
    private void moveBestEffort(String from, String to) {
        try {
            retry.run("moving " + from, () -> storage.move(from, to));
        } catch (RuntimeException e) {
            log.warn("Could not move {} to {}: {}", from, to, e.getMessage());
        }
    }

    /** "incoming/a.csv" becomes "processed/<run id>-a.csv", so a re-uploaded file name never overwrites history. */
    private static String destination(String prefix, EtlRun run, StoredObject file) {
        return prefix + run.getId() + "-" + file.key().substring(INCOMING.length());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
