package com.aether.app.pretrain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Runs once at startup and seeds the pretrainedData Firestore collection from every
 * .json file in the training-data directory (aether-app/training-data/).
 *
 * For each file:
 *   - id: filename without .json (sanitized)
 *   - title: metadata.agent_title from JSON
 *   - trainingContent: full JSON payload as string
 *   - fileName: original filename
 *
 * Deduplication: if a document with the same id already exists, the file is skipped.
 */
@Component
public class PretrainedDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PretrainedDataSeeder.class);

    private final PretrainedDataRepository pretrainedDataRepository;
    private final ObjectMapper objectMapper;
    private final String trainingDataPath;

    public PretrainedDataSeeder(PretrainedDataRepository pretrainedDataRepository,
                                ObjectMapper objectMapper,
                                @Value("${aether.training-data.path:training-data}") String trainingDataPath) {
        this.pretrainedDataRepository = pretrainedDataRepository;
        this.objectMapper = objectMapper;
        this.trainingDataPath = trainingDataPath;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path dir = Path.of(trainingDataPath);
        if (!Files.isDirectory(dir)) {
            log.info("Training data directory not found at {}, skipping pretrained data seeding", dir.toAbsolutePath());
            return;
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream
                    .filter(p -> Files.isRegularFile(p))
                    .filter(p -> {
                        String name = p.toString().toLowerCase();
                        return name.endsWith(".json") || name.endsWith(".txt");
                    })
                    .toList();
        } catch (IOException e) {
            log.error("Failed to list training-data directory: {}", e.getMessage());
            return;
        }

        if (files.isEmpty()) {
            log.info("No .json or .txt files found in {}", dir.toAbsolutePath());
            return;
        }

        log.info("Seeding pretrained data from {} file(s) in {}...", files.size(), dir.toAbsolutePath());

        Flux.fromIterable(files)
                .concatMap(this::seedFile)
                .blockLast(Duration.ofSeconds(60));

        log.info("Pretrained data seeding complete.");
    }

    private Mono<PretrainedData> seedFile(Path filePath) {
        String fileName = filePath.getFileName().toString();
        String docId = sanitizeDocId(fileName);

        String rawContent;
        try {
            rawContent = Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Could not read training-data file: {}", fileName, e);
            return Mono.empty();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawContent);
        } catch (Exception e) {
            log.warn("Skipping {} — invalid JSON: {}", fileName, e.getMessage());
            return Mono.empty();
        }

        String title = extractAgentTitle(root, fileName);
        String trainingContent = rawContent;

        return pretrainedDataRepository.existsById(docId)
                .flatMap(exists -> {
                    if (exists) {
                        log.info("Pretrained entry already exists, skipping: '{}' (id={})", title, docId);
                        return pretrainedDataRepository.findById(docId);
                    }
                    PretrainedData entry = new PretrainedData();
                    entry.setId(docId);
                    entry.setTitle(title);
                    entry.setTrainingContent(trainingContent);
                    entry.setFileName(fileName);
                    Instant now = Instant.now();
                    entry.setCreatedAt(now);
                    entry.setUpdatedAt(now);
                    return pretrainedDataRepository.save(entry)
                            .doOnSuccess(saved -> log.info("Seeded pretrained entry: '{}' (id={})",
                                    saved.getTitle(), saved.getId()));
                });
    }

    private String extractAgentTitle(JsonNode root, String fileName) {
        JsonNode metadata = root.path("metadata");
        JsonNode agentTitle = metadata.path("agent_title");
        if (agentTitle != null && agentTitle.isTextual()) {
            return agentTitle.asText();
        }
        return fileName.replaceAll("\\.(json|txt)$", "");
    }

    private String sanitizeDocId(String fileName) {
        String lower = fileName.toLowerCase();
        String base = lower.endsWith(".json")
                ? fileName.substring(0, fileName.length() - 5)
                : lower.endsWith(".txt")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
        return base.replaceAll("\\s+", "-").replaceAll("-+", "-").replaceAll("^[-_]|[-_]$", "")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
