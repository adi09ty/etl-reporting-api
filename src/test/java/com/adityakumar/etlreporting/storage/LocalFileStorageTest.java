package com.adityakumar.etlreporting.storage;

import com.adityakumar.etlreporting.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileStorageTest {

    @TempDir
    Path root;

    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage(new AppProperties(
                new AppProperties.Storage("local", root.toString(), new AppProperties.S3(null, "us-east-1", "b", null, null, true)),
                new AppProperties.Rates("https://example.test", "USD"),
                new AppProperties.Etl(3, Duration.ofMillis(1), 500),
                new AppProperties.Alerts(null)));
    }

    private void write(String key, String content) throws IOException {
        Path file = root.resolve(key);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void listsFilesUnderAPrefixSortedByKeyWithSizeAndEtag() throws IOException {
        write("incoming/b.csv", "bb");
        write("incoming/a.csv", "a");
        write("processed/old.csv", "x");

        List<StoredObject> found = storage.list("incoming/");

        assertEquals(List.of("incoming/a.csv", "incoming/b.csv"), found.stream().map(StoredObject::key).toList());
        assertEquals(1, found.get(0).size());
        assertEquals(32, found.get(0).etag().length());
    }

    @Test
    void theEtagChangesWhenTheContentChanges() throws IOException {
        write("incoming/a.csv", "one");
        String before = storage.list("incoming/").get(0).etag();

        write("incoming/a.csv", "two");

        assertNotEquals(before, storage.list("incoming/").get(0).etag());
    }

    @Test
    void aMissingPrefixIsJustEmpty() {
        assertEquals(List.of(), storage.list("nothing-here/"));
    }

    @Test
    void readsTheBytesOfAFile() throws IOException {
        write("incoming/a.csv", "héllo");

        assertArrayEquals("héllo".getBytes(StandardCharsets.UTF_8), storage.read("incoming/a.csv"));
    }

    @Test
    void movingCreatesTheTargetFolderAndRemovesTheOriginal() throws IOException {
        write("incoming/a.csv", "data");

        storage.move("incoming/a.csv", "processed/7-a.csv");

        assertFalse(Files.exists(root.resolve("incoming/a.csv")));
        assertEquals("data", Files.readString(root.resolve("processed/7-a.csv")));
    }

    @Test
    void movingOntoAnExistingFileReplacesIt() throws IOException {
        write("incoming/a.csv", "new");
        write("processed/a.csv", "old");

        storage.move("incoming/a.csv", "processed/a.csv");

        assertEquals("new", Files.readString(root.resolve("processed/a.csv")));
    }

    @Test
    void keysCannotEscapeTheStorageFolder() {
        assertThrows(IllegalArgumentException.class, () -> storage.read("../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> storage.move("incoming/a.csv", "../../elsewhere.csv"));
        assertTrue(storage.list("incoming/").isEmpty());
    }
}
