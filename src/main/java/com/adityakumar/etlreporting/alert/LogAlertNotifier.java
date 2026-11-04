package com.adityakumar.etlreporting.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Always on: writes the alert to the "alerts" log at ERROR level so a log monitor can pick it up. */
@Component
public class LogAlertNotifier implements AlertNotifier {

    private static final Logger alerts = LoggerFactory.getLogger("alerts");

    @Override
    public void notify(String subject, String details) {
        alerts.error("ALERT: {} - {}", subject, details);
    }
}
