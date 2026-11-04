package com.adityakumar.etlreporting;

import com.adityakumar.etlreporting.domain.EtlRun;
import com.adityakumar.etlreporting.domain.RunStatus;
import com.adityakumar.etlreporting.domain.TriggerType;
import com.adityakumar.etlreporting.rates.RatesRefreshJob;
import com.adityakumar.etlreporting.rates.RatesSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RatesRefreshJobTest extends IntegrationTestBase {

    @Autowired RatesRefreshJob job;

    private static RatesSnapshot snapshot(String base, Map<String, BigDecimal> rates) {
        return new RatesSnapshot(base, LocalDate.of(2026, 3, 1), rates);
    }

    @Test
    void storesTheFetchedRatesAndAddsTheBaseCurrencyAtOne() {
        when(ratesClient.fetchLatest("USD")).thenReturn(snapshot("USD",
                Map.of("INR", new BigDecimal("83.5"), "EUR", new BigDecimal("0.9"))));

        EtlRun run = job.run(TriggerType.MANUAL);

        assertEquals(RunStatus.SUCCEEDED, run.getStatus());
        assertEquals(2, run.getRowsRead());
        assertEquals(3, run.getRowsInserted());
        assertEquals(3, exchangeRates.count());
        assertEquals(0, BigDecimal.ONE.compareTo(exchangeRates.findById("USD").orElseThrow().getRate()));
        assertEquals(0, new BigDecimal("83.5").compareTo(exchangeRates.findById("INR").orElseThrow().getRate()));
    }

    @Test
    void aSecondRunUpdatesTheExistingRates() {
        when(ratesClient.fetchLatest("USD")).thenReturn(snapshot("USD", Map.of("INR", new BigDecimal("83.5"))));
        job.run(TriggerType.MANUAL);

        when(ratesClient.fetchLatest("USD")).thenReturn(snapshot("USD", Map.of("INR", new BigDecimal("84.0"))));
        EtlRun second = job.run(TriggerType.SCHEDULED);

        assertEquals(0, second.getRowsInserted());
        assertEquals(2, second.getRowsUpdated());
        assertEquals(2, exchangeRates.count());
        assertEquals(0, new BigDecimal("84.0").compareTo(exchangeRates.findById("INR").orElseThrow().getRate()));
    }

    @Test
    void aTemporaryApiFailureIsRetriedAndThenSucceeds() {
        when(ratesClient.fetchLatest(anyString()))
                .thenThrow(new RestClientException("timeout"))
                .thenThrow(new RestClientException("timeout"))
                .thenReturn(snapshot("USD", Map.of("INR", new BigDecimal("83.5"))));

        EtlRun run = job.run(TriggerType.MANUAL);

        assertEquals(RunStatus.SUCCEEDED, run.getStatus());
        verify(ratesClient, times(3)).fetchLatest("USD");
        assertTrue(alerts.subjects.isEmpty());
    }

    @Test
    void whenTheApiStaysDownTheRunFailsAlertsAndKeepsTheOldRates() {
        when(ratesClient.fetchLatest("USD")).thenReturn(snapshot("USD", Map.of("INR", new BigDecimal("83.5"))));
        job.run(TriggerType.MANUAL);

        when(ratesClient.fetchLatest(anyString())).thenThrow(new RestClientException("503 Service Unavailable"));
        EtlRun failed = job.run(TriggerType.SCHEDULED);

        assertEquals(RunStatus.FAILED, failed.getStatus());
        assertTrue(failed.getErrorMessage().contains("503"));
        verify(ratesClient, times(1 + 3)).fetchLatest("USD");
        assertEquals(1, alerts.subjects.size());
        assertTrue(alerts.subjects.get(0).contains("RATES_REFRESH"));
        assertEquals(0, new BigDecimal("83.5").compareTo(exchangeRates.findById("INR").orElseThrow().getRate()));
    }

    @Test
    void nonsenseDataIsRefusedWithoutRetryingAndNothingIsStored() {
        when(ratesClient.fetchLatest("USD")).thenReturn(snapshot("USD", Map.of("INR", BigDecimal.ZERO)));

        EtlRun run = job.run(TriggerType.MANUAL);

        assertEquals(RunStatus.FAILED, run.getStatus());
        assertTrue(run.getErrorMessage().contains("Invalid rate for INR"));
        assertEquals(0, exchangeRates.count());
        assertEquals(1, alerts.subjects.size());
    }

    @Test
    void ratesQuotedInTheWrongBaseCurrencyAreRefused() {
        when(ratesClient.fetchLatest("USD")).thenReturn(snapshot("EUR", Map.of("INR", new BigDecimal("90"))));

        EtlRun run = job.run(TriggerType.MANUAL);

        assertEquals(RunStatus.FAILED, run.getStatus());
        assertEquals(0, exchangeRates.count());
    }
}
