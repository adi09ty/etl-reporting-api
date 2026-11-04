package com.adityakumar.etlreporting.rates;

/** The REST source of exchange rates. */
public interface RatesClient {

    RatesSnapshot fetchLatest(String baseCurrency);
}
