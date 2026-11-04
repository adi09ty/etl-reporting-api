package com.adityakumar.etlreporting.storage;

import java.util.List;

/**
 * Where input files live. Keys look like "incoming/orders-2026-03-01.csv".
 * Implementations: a local folder (default, zero setup) and an S3-compatible bucket.
 */
public interface ObjectStorage {

    /** All files whose key starts with the prefix, sorted by key. */
    List<StoredObject> list(String prefix);

    byte[] read(String key);

    /** Moves a file to a new key (copy, then delete the original). */
    void move(String fromKey, String toKey);
}
