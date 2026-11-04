package com.adityakumar.etlreporting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Typed view of the "app.*" settings in application.properties. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue Storage storage,
        @DefaultValue Rates rates,
        @DefaultValue Etl etl,
        @DefaultValue Alerts alerts) {

    public record Storage(
            @DefaultValue("local") String type,
            @DefaultValue("./data") String localDir,
            @DefaultValue S3 s3) {
    }

    public record S3(
            String endpoint,
            @DefaultValue("us-east-1") String region,
            @DefaultValue("etl-data") String bucket,
            String accessKey,
            String secretKey,
            @DefaultValue("true") boolean pathStyle) {
    }

    public record Rates(
            @DefaultValue("https://api.frankfurter.dev") String baseUrl,
            @DefaultValue("USD") String baseCurrency) {
    }

    public record Etl(
            @DefaultValue("3") int retryAttempts,
            @DefaultValue("500ms") Duration retryInitialDelay,
            @DefaultValue("500") int chunkSize) {
    }

    public record Alerts(String webhookUrl) {
    }
}
