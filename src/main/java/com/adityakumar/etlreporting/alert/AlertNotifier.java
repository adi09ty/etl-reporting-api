package com.adityakumar.etlreporting.alert;

/** A channel that tells people a job needs attention. */
public interface AlertNotifier {

    void notify(String subject, String details);
}
