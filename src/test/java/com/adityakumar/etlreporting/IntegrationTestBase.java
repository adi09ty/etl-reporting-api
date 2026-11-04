package com.adityakumar.etlreporting;

import com.adityakumar.etlreporting.alert.AlertNotifier;
import com.adityakumar.etlreporting.rates.RatesClient;
import com.adityakumar.etlreporting.repository.EtlRunRepository;
import com.adityakumar.etlreporting.repository.ExchangeRateRepository;
import com.adityakumar.etlreporting.repository.ProcessedFileRepository;
import com.adityakumar.etlreporting.repository.RejectedRowRepository;
import com.adityakumar.etlreporting.repository.SalesOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * Shared setup for the integration tests: one Spring context, an H2 database, a temporary folder as the
 * storage bucket, a mocked exchange-rate API and a recorder that captures alerts. Every test starts clean.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestBase.RecordingConfig.class)
abstract class IntegrationTestBase {

    static final Path STORAGE = createStorageFolder();

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.local-dir", STORAGE::toString);
    }

    /** Remembers every alert subject that was sent. */
    static class RecordingNotifier implements AlertNotifier {
        final List<String> subjects = new CopyOnWriteArrayList<>();

        @Override
        public void notify(String subject, String details) {
            subjects.add(subject);
        }
    }

    @TestConfiguration
    static class RecordingConfig {
        @Bean
        RecordingNotifier recordingNotifier() {
            return new RecordingNotifier();
        }
    }

    @MockitoBean
    RatesClient ratesClient;

    @Autowired MockMvc mvc;
    @Autowired RecordingNotifier alerts;
    @Autowired SalesOrderRepository orders;
    @Autowired ExchangeRateRepository exchangeRates;
    @Autowired EtlRunRepository runs;
    @Autowired RejectedRowRepository rejectedRows;
    @Autowired ProcessedFileRepository processedFiles;

    @BeforeEach
    void cleanSlate() throws IOException {
        orders.deleteAll();
        exchangeRates.deleteAll();
        rejectedRows.deleteAll();
        processedFiles.deleteAll();
        runs.deleteAll();
        alerts.subjects.clear();
        try (Stream<Path> files = Files.walk(STORAGE)) {
            files.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(STORAGE)).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    void putFile(String key, String content) {
        try {
            Path target = STORAGE.resolve(key);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    boolean fileExists(String key) {
        return Files.exists(STORAGE.resolve(key));
    }

    List<String> filesUnder(String prefix) throws IOException {
        Path dir = STORAGE.resolve(prefix);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private static Path createStorageFolder() {
        try {
            return Files.createTempDirectory("etl-test-storage");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
