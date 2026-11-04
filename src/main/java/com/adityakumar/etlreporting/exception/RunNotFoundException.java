package com.adityakumar.etlreporting.exception;

public class RunNotFoundException extends RuntimeException {
    public RunNotFoundException(Long id) {
        super("ETL run not found: " + id);
    }
}
