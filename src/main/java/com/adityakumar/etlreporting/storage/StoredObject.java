package com.adityakumar.etlreporting.storage;

/** A file in the store. The etag changes whenever the content changes (for S3 it is the object's ETag). */
public record StoredObject(String key, String etag, long size) {
}
