package com.adityakumar.etlreporting.exception;

/** The whole file is unusable (for example missing columns). Retrying will not help. */
public class InvalidFileException extends RuntimeException {
    public InvalidFileException(String message) {
        super(message);
    }
}
