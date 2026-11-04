package com.adityakumar.etlreporting.storage;

import com.adityakumar.etlreporting.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.ArrayList;
import java.util.List;

/** An S3 bucket (AWS or any S3-compatible store). Requires the "s3" storage type. */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3;
    private final String bucket;

    public S3ObjectStorage(S3Client s3, AppProperties properties) {
        this.s3 = s3;
        this.bucket = properties.storage().s3().bucket();
    }

    @Override
    public List<StoredObject> list(String prefix) {
        List<StoredObject> found = new ArrayList<>();
        String continuationToken = null;
        do {
            ListObjectsV2Response page = s3.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .continuationToken(continuationToken)
                    .build());
            for (S3Object object : page.contents()) {
                if (!object.key().endsWith("/")) {
                    found.add(new StoredObject(object.key(), stripQuotes(object.eTag()), object.size()));
                }
            }
            continuationToken = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
        } while (continuationToken != null);
        found.sort((a, b) -> a.key().compareTo(b.key()));
        return found;
    }

    @Override
    public byte[] read(String key) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    @Override
    public void move(String fromKey, String toKey) {
        // Copy first, delete second: if we fail in between, the file exists twice rather than not at all.
        s3.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket).sourceKey(fromKey)
                .destinationBucket(bucket).destinationKey(toKey)
                .build());
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(fromKey).build());
    }

    private static String stripQuotes(String etag) {
        return etag == null ? "" : etag.replace("\"", "");
    }
}
