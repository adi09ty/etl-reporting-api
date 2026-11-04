package com.adityakumar.etlreporting.alert;

import com.adityakumar.etlreporting.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Optional: posts the alert as JSON ({"text": "..."}, which Slack-style webhooks accept) to a configured URL. */
@Component
@ConditionalOnExpression("'${app.alerts.webhook-url:}' != ''")
public class WebhookAlertNotifier implements AlertNotifier {

    private final RestClient client;
    private final String url;

    public WebhookAlertNotifier(RestClient.Builder builder, AppProperties properties) {
        this.client = builder.build();
        this.url = properties.alerts().webhookUrl();
    }

    @Override
    public void notify(String subject, String details) {
        client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", subject + "\n" + details))
                .retrieve()
                .toBodilessEntity();
    }
}
