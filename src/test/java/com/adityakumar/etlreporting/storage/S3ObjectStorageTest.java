package com.adityakumar.etlreporting.storage;

import com.adityakumar.etlreporting.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The S3 client is mocked here; the real client is exercised by running the Docker Compose stack. */
class S3ObjectStorageTest {

    private S3Client s3;
    private S3ObjectStorage storage;

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class);
        storage = new S3ObjectStorage(s3, new AppProperties(
                new AppProperties.Storage("s3", "./data", new AppProperties.S3("http://s3.test", "us-east-1", "my-bucket", "k", "s", true)),
                new AppProperties.Rates("https://example.test", "USD"),
                new AppProperties.Etl(3, Duration.ofMillis(1), 500),
                new AppProperties.Alerts(null)));
    }

    private static S3Object object(String key, String etag, long size) {
        return S3Object.builder().key(key).eTag(etag).size(size).build();
    }

    @Test
    void listsAllPagesSkipsFolderMarkersAndStripsEtagQuotes() {
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().isTruncated(true).nextContinuationToken("page2")
                        .contents(object("incoming/", "\"x\"", 0), object("incoming/b.csv", "\"bbb\"", 20)).build(),
                ListObjectsV2Response.builder().isTruncated(false)
                        .contents(object("incoming/a.csv", "\"aaa\"", 10)).build());

        List<StoredObject> found = storage.list("incoming/");

        assertEquals(List.of(new StoredObject("incoming/a.csv", "aaa", 10), new StoredObject("incoming/b.csv", "bbb", 20)), found);
        ArgumentCaptor<ListObjectsV2Request> requests = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3, org.mockito.Mockito.times(2)).listObjectsV2(requests.capture());
        assertEquals("my-bucket", requests.getAllValues().get(0).bucket());
        assertEquals("incoming/", requests.getAllValues().get(0).prefix());
        assertEquals("page2", requests.getAllValues().get(1).continuationToken());
    }

    @Test
    void readsTheObjectBytes() {
        byte[] content = "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content));

        assertArrayEquals(content, storage.read("incoming/a.csv"));

        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3).getObjectAsBytes(request.capture());
        assertEquals("my-bucket", request.getValue().bucket());
        assertEquals("incoming/a.csv", request.getValue().key());
    }

    @Test
    void movingCopiesFirstAndOnlyThenDeletesTheOriginal() {
        storage.move("incoming/a.csv", "processed/3-a.csv");

        InOrder order = inOrder(s3);
        ArgumentCaptor<CopyObjectRequest> copy = ArgumentCaptor.forClass(CopyObjectRequest.class);
        ArgumentCaptor<DeleteObjectRequest> delete = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        order.verify(s3).copyObject(copy.capture());
        order.verify(s3).deleteObject(delete.capture());
        assertEquals("incoming/a.csv", copy.getValue().sourceKey());
        assertEquals("processed/3-a.csv", copy.getValue().destinationKey());
        assertEquals("incoming/a.csv", delete.getValue().key());
    }
}
