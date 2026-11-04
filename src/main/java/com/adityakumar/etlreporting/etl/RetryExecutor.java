package com.adityakumar.etlreporting.etl;

import com.adityakumar.etlreporting.config.AppProperties;
import com.adityakumar.etlreporting.exception.InvalidFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Retries a call that may fail for transient reasons (network, S3 or API hiccups) with exponential back-off:
 * the wait doubles after every failed attempt. Errors that retrying cannot fix are thrown straight away.
 */
@Component
public class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);

    /** How the executor waits between attempts. Replaced in tests so they do not really sleep. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Sleeper sleeper;

    @Autowired
    public RetryExecutor(AppProperties properties) {
        this(properties.etl().retryAttempts(), properties.etl().retryInitialDelay(),
                duration -> Thread.sleep(duration.toMillis()));
    }

    public RetryExecutor(int maxAttempts, Duration initialDelay, Sleeper sleeper) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialDelay = initialDelay;
        this.sleeper = sleeper;
    }

    public <T> T call(String description, Supplier<T> action) {
        Duration delay = initialDelay;
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (InvalidFileException e) {
                throw e;
            } catch (RuntimeException e) {
                if (attempt >= maxAttempts) {
                    throw e;
                }
                log.warn("{} failed (attempt {} of {}): {}. Retrying in {} ms", description, attempt, maxAttempts,
                        e.getMessage(), delay.toMillis());
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while retrying " + description, interrupted);
                }
                delay = delay.multipliedBy(2);
            }
        }
    }

    public void run(String description, Runnable action) {
        call(description, () -> {
            action.run();
            return null;
        });
    }
}
