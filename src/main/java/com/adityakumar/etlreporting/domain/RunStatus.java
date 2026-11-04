package com.adityakumar.etlreporting.domain;

/** SUCCEEDED: nothing failed. PARTIAL: some files failed, some loaded. FAILED: nothing could be loaded. */
public enum RunStatus {
    RUNNING, SUCCEEDED, PARTIAL, FAILED
}
