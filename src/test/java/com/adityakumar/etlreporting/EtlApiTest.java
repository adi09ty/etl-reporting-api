package com.adityakumar.etlreporting;

import com.adityakumar.etlreporting.rates.RatesSnapshot;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EtlApiTest extends IntegrationTestBase {

    private static final String HEADER = "order_id,customer_id,category,amount,currency,order_date,status\n";

    @Test
    void triggeringTheImportReturnsTheRunSummaryAndShowsUpInTheHistory() throws Exception {
        putFile("incoming/api.csv", HEADER
                + "X-1,C1,Books,10.00,USD,2026-03-01,COMPLETED\n"
                + "X-2,C2,Books,oops,USD,2026-03-01,COMPLETED\n");

        String body = mvc.perform(post("/api/etl/jobs/orders-import/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobName").value("ORDERS_IMPORT"))
                .andExpect(jsonPath("$.trigger").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.filesProcessed").value(1))
                .andExpect(jsonPath("$.rowsRead").value(2))
                .andExpect(jsonPath("$.rowsInserted").value(1))
                .andExpect(jsonPath("$.rowsRejected").value(1))
                .andReturn().getResponse().getContentAsString();
        long runId = ((Number) JsonPath.read(body, "$.id")).longValue();

        mvc.perform(get("/api/etl/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(runId));
        mvc.perform(get("/api/etl/runs/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finishedAt").exists());
        mvc.perform(get("/api/etl/runs/" + runId + "/rejected-rows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reason").value("amount is not a number"))
                .andExpect(jsonPath("$.content[0].sourceFile").value("incoming/api.csv"))
                .andExpect(jsonPath("$.content[0].rowNumber").value(2));
    }

    @Test
    void aFailedRunIsStillReportedWithStatusFailed() throws Exception {
        putFile("incoming/bad.csv", "nothing,useful\n1,2\n");

        mvc.perform(post("/api/etl/jobs/orders-import/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.filesFailed").value(1))
                .andExpect(jsonPath("$.errorMessage").exists());
    }

    @Test
    void triggeringTheRatesRefreshStoresRates() throws Exception {
        when(ratesClient.fetchLatest("USD")).thenReturn(
                new RatesSnapshot("USD", LocalDate.of(2026, 3, 1), Map.of("INR", new BigDecimal("83.5"))));

        mvc.perform(post("/api/etl/jobs/rates-refresh/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobName").value("RATES_REFRESH"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.rowsInserted").value(2));
    }

    @Test
    void unknownRunsAreNotFound() throws Exception {
        mvc.perform(get("/api/etl/runs/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/etl/runs/999999/rejected-rows")).andExpect(status().isNotFound());
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
