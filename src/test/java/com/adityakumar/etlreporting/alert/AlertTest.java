package com.adityakumar.etlreporting.alert;

import com.adityakumar.etlreporting.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AlertTest {

    private static AppProperties properties(String webhookUrl) {
        return new AppProperties(
                new AppProperties.Storage("local", "./data", new AppProperties.S3(null, "us-east-1", "b", null, null, true)),
                new AppProperties.Rates("https://rates.test", "USD"),
                new AppProperties.Etl(3, Duration.ofMillis(1), 500),
                new AppProperties.Alerts(webhookUrl));
    }

    @Test
    void theWebhookReceivesTheAlertAsJsonText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WebhookAlertNotifier notifier = new WebhookAlertNotifier(builder, properties("https://hooks.test/alert"));
        server.expect(requestTo("https://hooks.test/alert"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.text").value("ORDERS_IMPORT run 5 FAILED\nbad.csv: broken"))
                .andRespond(withSuccess());

        notifier.notify("ORDERS_IMPORT run 5 FAILED", "bad.csv: broken");

        server.verify();
    }

    @Test
    void aWebhookErrorSurfacesAsAnException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WebhookAlertNotifier notifier = new WebhookAlertNotifier(builder, properties("https://hooks.test/alert"));
        server.expect(requestTo("https://hooks.test/alert")).andRespond(withServerError());

        assertThrows(RestClientResponseException.class, () -> notifier.notify("s", "d"));
    }

    @Test
    void aBrokenChannelNeverStopsTheOtherChannels() {
        List<String> received = new ArrayList<>();
        AlertNotifier broken = (subject, details) -> {
            throw new IllegalStateException("webhook is down");
        };
        AlertNotifier working = (subject, details) -> received.add(subject + "|" + details);
        AlertService service = new AlertService(List.of(broken, working));

        service.send("subject", "details");

        assertEquals(List.of("subject|details"), received);
    }
}
