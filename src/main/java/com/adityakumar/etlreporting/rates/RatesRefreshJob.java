package com.adityakumar.etlreporting.rates;

import com.adityakumar.etlreporting.alert.AlertService;
import com.adityakumar.etlreporting.config.AppProperties;
import com.adityakumar.etlreporting.domain.EtlRun;
import com.adityakumar.etlreporting.domain.JobName;
import com.adityakumar.etlreporting.domain.RunStatus;
import com.adityakumar.etlreporting.domain.TriggerType;
import com.adityakumar.etlreporting.etl.RetryExecutor;
import com.adityakumar.etlreporting.exception.JobAlreadyRunningException;
import com.adityakumar.etlreporting.repository.EtlRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetches the latest exchange rates from the REST API (with retries), checks them, and stores them.
 * If the API stays down, the run is recorded as FAILED, an alert goes out, and the previous rates stay in place.
 */
@Service
public class RatesRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(RatesRefreshJob.class);

    private final RatesClient client;
    private final RatesWriter writer;
    private final EtlRunRepository runs;
    private final RetryExecutor retry;
    private final AlertService alerts;
    private final AppProperties properties;
    private final Clock clock;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public RatesRefreshJob(RatesClient client, RatesWriter writer, EtlRunRepository runs, RetryExecutor retry,
                           AlertService alerts, AppProperties properties, Clock clock) {
        this.client = client;
        this.writer = writer;
        this.runs = runs;
        this.retry = retry;
        this.alerts = alerts;
        this.properties = properties;
        this.clock = clock;
    }

    public EtlRun run(TriggerType trigger) {
        if (!running.compareAndSet(false, true)) {
            throw new JobAlreadyRunningException(JobName.RATES_REFRESH);
        }
        EtlRun run = runs.save(new EtlRun(JobName.RATES_REFRESH, trigger, LocalDateTime.now(clock)));
        try {
            String base = properties.rates().baseCurrency();
            RatesSnapshot snapshot = retry.call("fetching exchange rates", () -> client.fetchLatest(base));
            validate(snapshot, base);
            RatesWriter.Result result = writer.save(snapshot);
            run.addRows(snapshot.rates().size(), result.inserted(), result.updated());
        } catch (RuntimeException e) {
            log.error("Rates refresh run {} failed", run.getId(), e);
            run.fail("Rates refresh failed: " + e.getMessage());
        } finally {
            run.finish(LocalDateTime.now(clock));
            runs.save(run);
            running.set(false);
        }
        log.info("Rates refresh run {} finished: status={} rates={} inserted={} updated={}",
                run.getId(), run.getStatus(), run.getRowsRead(), run.getRowsInserted(), run.getRowsUpdated());
        if (run.getStatus() == RunStatus.FAILED) {
            alerts.send("RATES_REFRESH run " + run.getId() + " FAILED", run.getErrorMessage());
        }
        return run;
    }

    /** Refuses data that would silently corrupt every report: wrong base, no rates, zero or negative rates. */
    private static void validate(RatesSnapshot snapshot, String expectedBase) {
        if (!expectedBase.equals(snapshot.base())) {
            throw new IllegalStateException("Expected base " + expectedBase + " but got " + snapshot.base());
        }
        if (snapshot.rates().isEmpty()) {
            throw new IllegalStateException("The rates API returned no rates");
        }
        snapshot.rates().forEach((currency, rate) -> {
            if (rate == null || rate.signum() <= 0) {
                throw new IllegalStateException("Invalid rate for " + currency + ": " + rate);
            }
        });
    }
}
