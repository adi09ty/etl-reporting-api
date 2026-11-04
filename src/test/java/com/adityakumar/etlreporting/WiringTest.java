package com.adityakumar.etlreporting;

import com.adityakumar.etlreporting.alert.AlertService;
import com.adityakumar.etlreporting.alert.WebhookAlertNotifier;
import com.adityakumar.etlreporting.etl.EtlScheduler;
import com.adityakumar.etlreporting.rates.FrankfurterRatesClient;
import com.adityakumar.etlreporting.rates.RatesClient;
import com.adityakumar.etlreporting.storage.LocalFileStorage;
import com.adityakumar.etlreporting.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Starts the application with the REAL beans (nothing mocked) so wiring mistakes show up in the build
 * instead of at startup in Docker. No network calls happen: scheduling is off and the clients are only created.
 */
@SpringBootTest(properties = {
        "app.alerts.webhook-url=https://hooks.example.test/alert",
        "app.etl.scheduling.enabled=true",
        "app.etl.initial-delay=PT1H"})
@ActiveProfiles("test")
class WiringTest {

    @Autowired ApplicationContext context;
    @Autowired RatesClient ratesClient;
    @Autowired ObjectStorage storage;
    @Autowired AlertService alerts;

    @Test
    void theRealRatesClientAndLocalStorageAreWired() {
        assertInstanceOf(FrankfurterRatesClient.class, ratesClient);
        assertInstanceOf(LocalFileStorage.class, storage);
    }

    @Test
    void theWebhookChannelIsCreatedWhenAUrlIsConfigured() {
        assertNotNull(context.getBean(WebhookAlertNotifier.class));
        assertNotNull(alerts);
    }

    @Test
    void theSchedulerIsCreatedWhenSchedulingIsEnabled() {
        assertNotNull(context.getBean(EtlScheduler.class));
    }

    @Test
    void theS3BeansAreNotCreatedInLocalMode() {
        assertThrows(org.springframework.beans.factory.NoSuchBeanDefinitionException.class,
                () -> context.getBean(software.amazon.awssdk.services.s3.S3Client.class));
    }
}
