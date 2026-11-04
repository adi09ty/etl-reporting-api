package com.adityakumar.etlreporting.etl;

import com.adityakumar.etlreporting.exception.InvalidFileException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryExecutorTest {

    private final List<Duration> waits = new ArrayList<>();
    private final RetryExecutor retry = new RetryExecutor(4, Duration.ofMillis(100), waits::add);

    @Test
    void successOnTheFirstTryNeverWaits() {
        assertEquals("ok", retry.call("thing", () -> "ok"));
        assertEquals(List.of(), waits);
    }

    @Test
    void retriesUntilItSucceedsAndDoublesTheWaitEachTime() {
        AtomicInteger calls = new AtomicInteger();

        String result = retry.call("thing", () -> {
            if (calls.incrementAndGet() < 4) {
                throw new IllegalStateException("flaky");
            }
            return "finally";
        });

        assertEquals("finally", result);
        assertEquals(4, calls.get());
        assertEquals(List.of(Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofMillis(400)), waits);
    }

    @Test
    void givesUpAfterTheMaximumAttemptsAndThrowsTheLastError() {
        AtomicInteger calls = new AtomicInteger();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> retry.call("thing", () -> {
            throw new IllegalStateException("down #" + calls.incrementAndGet());
        }));

        assertEquals("down #4", thrown.getMessage());
        assertEquals(4, calls.get());
        assertEquals(3, waits.size());
    }

    @Test
    void errorsThatRetryingCannotFixAreThrownImmediately() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(InvalidFileException.class, () -> retry.call("thing", () -> {
            calls.incrementAndGet();
            throw new InvalidFileException("bad file");
        }));

        assertEquals(1, calls.get());
        assertEquals(List.of(), waits);
    }

    @Test
    void aMaximumOfOneAttemptMeansNoRetrying() {
        RetryExecutor once = new RetryExecutor(1, Duration.ofMillis(100), waits::add);
        AtomicInteger calls = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> once.call("thing", () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("x");
        }));

        assertEquals(1, calls.get());
    }
}
