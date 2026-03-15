package com.aether.app.pretrain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs once at startup and seeds the pretrainData Firestore collection from every
 * .txt file in the training-data directory (aether-app/training-data/).
 *
 * File format expected:
 *   Line 1 → "Title: <title text>"
 *   Remaining lines → training content
 *
 * Deduplication: if a document with the same id (filename without .txt) already exists, the file is skipped.
 * Adding a new file is as simple as dropping it into training-data/.
 */
@Component
public class PretrainDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PretrainDataSeeder.class);
    private static final String TITLE_PREFIX = "Title:";

    private final PretrainDataRepository pretrainDataRepository;
    private final String trainingDataPath;

    public PretrainDataSeeder(PretrainDataRepository pretrainDataRepository,
                              @Value("${aether.training-data.path:training-data}") String trainingDataPath) {
        this.pretrainDataRepository = pretrainDataRepository;
        this.trainingDataPath = trainingDataPath;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path dir = Path.of(trainingDataPath);
        if (!Files.isDirectory(dir)) {
            log.info("Training data directory not found at {}, skipping pretrain seeding", dir.toAbsolutePath());
            return;
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".txt"))
                    .toList();
        } catch (IOException e) {
            log.error("Failed to list training-data directory: {}", e.getMessage());
            return;
        }

        if (files.isEmpty()) {
            log.info("No .txt files found in {}", dir.toAbsolutePath());
            return;
        }

        log.info("Seeding pretrain data from {} file(s) in {}...", files.size(), dir.toAbsolutePath());

        Flux.fromIterable(files)
                .concatMap(this::seedFile)
                .blockLast(Duration.ofSeconds(60));

        log.info("Pretrain data seeding complete.");
    }

    private Mono<PretrainData> seedFile(Path filePath) {
        String fileName = filePath.getFileName().toString();
        String docId = fileName.endsWith(".txt")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;

        String rawContent;
        try {
            rawContent = Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Could not read training-data file: {}", fileName, e);
            return Mono.empty();
        }

        String[] lines = rawContent.split("\n", 2);
        if (lines.length < 1 || !lines[0].startsWith(TITLE_PREFIX)) {
            log.warn("Skipping {} — first line must be 'Title: ...'", fileName);
            return Mono.empty();
        }

        String title = lines[0].substring(TITLE_PREFIX.length()).trim();
        String trainingContent = lines.length > 1 ? lines[1].stripLeading() : "";

        return pretrainDataRepository.existsById(docId)
                .flatMap(exists -> {
                    if (exists) {
                        log.info("Pretrain entry already exists, skipping: '{}' (id={})", title, docId);
                        return pretrainDataRepository.findById(docId);
                    }
                    PretrainData entry = new PretrainData();
                    entry.setId(docId);
                    entry.setTitle(title);
                    entry.setTrainingContent(trainingContent);
                    entry.setFileName(fileName);
                    Instant now = Instant.now();
                    entry.setCreatedAt(now);
                    entry.setUpdatedAt(now);
                    return pretrainDataRepository.save(entry)
                            .doOnSuccess(saved -> log.info("Seeded pretrain entry: '{}' (id={})",
                                    saved.getTitle(), saved.getId()));
                });
    }
}
