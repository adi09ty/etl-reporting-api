package com.adityakumar.etlreporting.rates;

import com.adityakumar.etlreporting.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FrankfurterRatesClientTest {

    private MockRestServiceServer server;
    private FrankfurterRatesClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FrankfurterRatesClient(builder, new AppProperties(
                new AppProperties.Storage("local", "./data", new AppProperties.S3(null, "us-east-1", "b", null, null, true)),
                new AppProperties.Rates("https://rates.test", "USD"),
                new AppProperties.Etl(3, Duration.ofMillis(1), 500),
                new AppProperties.Alerts(null)));
    }

    @Test
    void parsesTheApiAnswer() {
        server.expect(requestTo("https://rates.test/v1/latest?base=USD"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"amount":1.0,"base":"USD","date":"2026-11-03","rates":{"EUR":0.89453,"INR":94.75}}
                        """, MediaType.APPLICATION_JSON));

        RatesSnapshot snapshot = client.fetchLatest("USD");

        assertEquals("USD", snapshot.base());
        assertEquals(LocalDate.of(2026, 11, 3), snapshot.date());
        assertEquals(2, snapshot.rates().size());
        assertEquals(0, new BigDecimal("94.75").compareTo(snapshot.rates().get("INR")));
        server.verify();
    }

    @Test
    void aServerErrorSurfacesAsAnExceptionSoTheJobCanRetry() {
        server.expect(requestTo("https://rates.test/v1/latest?base=USD")).andRespond(withServerError());

        assertThrows(RestClientResponseException.class, () -> client.fetchLatest("USD"));
    }

    @Test
    void anUnreadableAnswerIsAnError() {
        server.expect(requestTo("https://rates.test/v1/latest?base=USD"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> client.fetchLatest("USD"));
    }
}
