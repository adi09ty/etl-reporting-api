package com.adityakumar.etlreporting.storage;

import com.adityakumar.etlreporting.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/** A folder on disk acting as the bucket, so the app runs with no external services. */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements ObjectStorage {

    private final Path root;

    public LocalFileStorage(AppProperties properties) {
        this.root = Path.of(properties.storage().localDir()).toAbsolutePath().normalize();
    }

    @Override
    public List<StoredObject> list(String prefix) {
        Path dir = resolve(prefix);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile)
                    .map(this::toStoredObject)
                    .sorted((a, b) -> a.key().compareTo(b.key()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + prefix, e);
        }
    }

    @Override
    public byte[] read(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + key, e);
        }
    }

    @Override
    public void move(String fromKey, String toKey) {
        Path source = resolve(fromKey);
        Path target = resolve(toKey);
        try {
            Files.createDirectories(target.getParent());
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not move " + fromKey + " to " + toKey, e);
        }
    }

    /** Keys never leave the storage folder, whatever they contain. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Key escapes the storage folder: " + key);
        }
        return path;
    }

    private StoredObject toStoredObject(Path file) {
        try {
            byte[] content = Files.readAllBytes(file);
            String key = root.relativize(file).toString().replace('\\', '/');
            return new StoredObject(key, md5(content), content.length);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    /** MD5 hex, which is also what S3 reports as the ETag of a simple upload. */
    private static String md5(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is not available", e);
        }
    }
}
