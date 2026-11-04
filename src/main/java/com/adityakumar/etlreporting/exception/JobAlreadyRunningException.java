package com.adityakumar.etlreporting.exception;

import com.adityakumar.etlreporting.domain.JobName;

public class JobAlreadyRunningException extends RuntimeException {
    public JobAlreadyRunningException(JobName job) {
        super("Job " + job + " is already running");
    }
}
