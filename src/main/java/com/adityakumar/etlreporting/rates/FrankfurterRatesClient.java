package com.adityakumar.etlreporting.rates;

import com.adityakumar.etlreporting.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Reads rates from the public Frankfurter API (European Central Bank reference rates, no API key). */
@Component
public class FrankfurterRatesClient implements RatesClient {

    /** The shape of the API's JSON answer. Unknown fields (like "amount") are ignored. */
    record Response(String base, LocalDate date, Map<String, BigDecimal> rates) {
    }

    private final RestClient client;

    public FrankfurterRatesClient(RestClient.Builder builder, AppProperties properties) {
        this.client = builder.baseUrl(properties.rates().baseUrl()).build();
    }

    @Override
    public RatesSnapshot fetchLatest(String baseCurrency) {
        Response response = client.get()
                .uri("/v1/latest?base={base}", baseCurrency)
                .retrieve()
                .body(Response.class);
        if (response == null || response.rates() == null || response.date() == null) {
            throw new IllegalStateException("The rates API returned an empty or unreadable answer");
        }
        return new RatesSnapshot(response.base(), response.date(), response.rates());
    }
}
