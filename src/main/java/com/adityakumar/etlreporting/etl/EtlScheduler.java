package com.adityakumar.etlreporting.etl;

import com.adityakumar.etlreporting.domain.TriggerType;
import com.adityakumar.etlreporting.exception.JobAlreadyRunningException;
import com.adityakumar.etlreporting.rates.RatesRefreshJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs both jobs on a timer. Intervals are ISO-8601 durations counted from the end of the previous run. */
@Component
@ConditionalOnProperty(name = "app.etl.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class EtlScheduler {

    private static final Logger log = LoggerFactory.getLogger(EtlScheduler.class);

    private final OrdersImportJob ordersImport;
    private final RatesRefreshJob ratesRefresh;

    public EtlScheduler(OrdersImportJob ordersImport, RatesRefreshJob ratesRefresh) {
        this.ordersImport = ordersImport;
        this.ratesRefresh = ratesRefresh;
    }

    @Scheduled(initialDelayString = "${app.etl.initial-delay:PT15S}",
            fixedDelayString = "${app.etl.orders-import-interval:PT1M}")
    void importOrders() {
        try {
            ordersImport.run(TriggerType.SCHEDULED);
        } catch (JobAlreadyRunningException e) {
            log.debug("Skipping scheduled orders import: {}", e.getMessage());
        }
    }

    @Scheduled(initialDelayString = "${app.etl.initial-delay:PT15S}",
            fixedDelayString = "${app.etl.rates-refresh-interval:PT6H}")
    void refreshRates() {
        try {
            ratesRefresh.run(TriggerType.SCHEDULED);
        } catch (JobAlreadyRunningException e) {
            log.debug("Skipping scheduled rates refresh: {}", e.getMessage());
        }
    }
}
