package com.adityakumar.etlreporting.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** Sends an alert through every channel. One broken channel never stops the others, and never breaks a job. */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final List<AlertNotifier> notifiers;

    public AlertService(List<AlertNotifier> notifiers) {
        this.notifiers = notifiers;
    }

    public void send(String subject, String details) {
        for (AlertNotifier notifier : notifiers) {
            try {
                notifier.notify(subject, details);
            } catch (RuntimeException e) {
                log.warn("Alert channel {} failed: {}", notifier.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
