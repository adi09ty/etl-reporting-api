package com.adityakumar.etlreporting;

import com.adityakumar.etlreporting.storage.ObjectStorage;
import com.adityakumar.etlreporting.storage.S3ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The S3 mode with its real configuration. Creating the client does not contact the endpoint. */
@SpringBootTest(properties = {
        "app.storage.type=s3",
        "app.storage.s3.endpoint=http://s3.example.test:9090",
        "app.storage.s3.bucket=etl-data",
        "app.storage.s3.access-key=test-key",
        "app.storage.s3.secret-key=test-secret",
        "app.etl.scheduling.enabled=false"})
@ActiveProfiles("test")
class S3WiringTest {

    @Autowired ObjectStorage storage;
    @Autowired S3Client s3Client;

    @Test
    void s3StorageAndClientAreWiredFromTheProperties() {
        assertInstanceOf(S3ObjectStorage.class, storage);
        assertNotNull(s3Client);
    }
}
