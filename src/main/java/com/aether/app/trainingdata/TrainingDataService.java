package com.aether.app.trainingdata;

import com.aether.app.common.PageInput;
import com.aether.app.common.PagedResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TrainingDataService {

    private final TrainingDataRepository trainingDataRepository;
    private final ObjectMapper objectMapper;

    public TrainingDataService(TrainingDataRepository trainingDataRepository, ObjectMapper objectMapper) {
        this.trainingDataRepository = trainingDataRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Parse content JSON to list of key-value entries. Supports legacy array and v2 wrapper.
     */
    public List<TrainingDataEntry> parseEntries(String content) {
        TrainingContent tc = TrainingContent.fromJson(content, objectMapper);
        List<TrainingDataEntry> result = new ArrayList<>();
        if (tc.getEntries() == null) {
            return result;
        }
        for (Map<String, String> m : tc.getEntries()) {
            if (m == null) {
                continue;
            }
            String k = m.get("key");
            String v = m.get("value");
            if (k != null) {
                result.add(new TrainingDataEntry(k, v != null ? v : ""));
            }
        }
        return result;
    }

    /**
     * Structured pricing facts stored in v2 content.
     */
    public List<PricingFact> parsePricingFacts(String content) {
        TrainingContent tc = TrainingContent.fromJson(content, objectMapper);
        List<PricingFact> out = new ArrayList<>();
        if (tc.getPricingFacts() == null) {
            return out;
        }
        for (Map<String, Object> m : tc.getPricingFacts()) {
            try {
                out.add(TrainingContent.mapToFact(m, objectMapper));
            } catch (Exception ignored) {
                // skip malformed fact
            }
        }
        return out;
    }

    public String serializeContent(List<TrainingDataEntryInput> entries, List<PricingFactInput> pricingFacts) throws Exception {
        TrainingContent tc = new TrainingContent();
        tc.setVersion(TrainingContent.VERSION);
        List<Map<String, String>> entryMaps = new ArrayList<>();
        if (entries != null) {
            for (TrainingDataEntryInput e : entries) {
                if (e == null || e.getKey() == null) {
                    continue;
                }
                entryMaps.add(Map.of(
                        "key", e.getKey(),
                        "value", e.getValue() != null ? e.getValue() : ""));
            }
        }
        tc.setEntries(entryMaps);

        List<Map<String, Object>> factMaps = new ArrayList<>();
        if (pricingFacts != null) {
            for (PricingFactInput pf : pricingFacts) {
                if (pf == null) {
                    continue;
                }
                PricingFact fact = fromInput(pf);
                if (fact.getId() == null || fact.getId().isBlank()) {
                    fact.setId(TrainingContent.newFactId());
                }
                factMaps.add(objectMapper.convertValue(fact, new TypeReference<>() {}));
            }
        }
        tc.setPricingFacts(factMaps);
        return TrainingContent.toJson(tc, objectMapper);
    }

    private static PricingFact fromInput(PricingFactInput in) {
        PricingFact f = new PricingFact();
        f.setId(in.getId());
        f.setProjectType(in.getProjectType());
        f.setMaterial(in.getMaterial());
        f.setUnit(in.getUnit());
        f.setPriceMin(in.getPriceMin());
        f.setPriceMax(in.getPriceMax());
        f.setPricePoint(in.getPricePoint());
        f.setIncludesLabor(in.getIncludesLabor());
        f.setCondition(in.getCondition());
        f.setNotes(in.getNotes());
        f.setSource(in.getSource());
        f.setConfidence(in.getConfidence());
        f.setBasedOnCount(in.getBasedOnCount());
        f.setObservedAt(in.getObservedAt());
        return f;
    }

    /**
     * Merge new facts with existing by id; overlapping semantic keys widen price range.
     */
    public List<PricingFactInput> mergePricingFacts(List<PricingFact> existing, List<PricingFactInput> incoming) {
        Map<String, PricingFactInput> byId = new LinkedHashMap<>();
        Map<String, PricingFactInput> bySemantic = new LinkedHashMap<>();
        for (PricingFact f : existing) {
            PricingFactInput pi = toInput(f);
            if (pi.getId() != null && !pi.getId().isBlank()) {
                byId.put(pi.getId(), pi);
            }
            String sem = semanticKey(pi);
            if (!sem.isEmpty()) {
                bySemantic.putIfAbsent(sem, pi);
            }
        }
        for (PricingFactInput inc : incoming) {
            if (inc == null) {
                continue;
            }
            if (inc.getId() != null && !inc.getId().isBlank() && byId.containsKey(inc.getId())) {
                byId.put(inc.getId(), widenMerge(byId.get(inc.getId()), inc));
                continue;
            }
            String sem = semanticKey(inc);
            if (!sem.isEmpty() && bySemantic.containsKey(sem)) {
                PricingFactInput prev = bySemantic.get(sem);
                PricingFactInput merged = widenMerge(prev, inc);
                if (merged.getId() != null) {
                    byId.put(merged.getId(), merged);
                }
                bySemantic.put(sem, merged);
            } else {
                if (inc.getId() == null || inc.getId().isBlank()) {
                    inc.setId(TrainingContent.newFactId());
                }
                byId.put(inc.getId(), inc);
                if (!sem.isEmpty()) {
                    bySemantic.put(sem, inc);
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    private static String semanticKey(PricingFactInput f) {
        if (f == null) {
            return "";
        }
        return String.join("|",
                nz(f.getProjectType()).toLowerCase(Locale.ROOT),
                nz(f.getMaterial()).toLowerCase(Locale.ROOT),
                nz(f.getUnit()).toLowerCase(Locale.ROOT),
                nz(f.getCondition()).toLowerCase(Locale.ROOT));
    }

    private static String nz(String s) {
        return s != null ? s.trim() : "";
    }

    private static PricingFactInput widenMerge(PricingFactInput a, PricingFactInput b) {
        Double min = minOf(a.getPriceMin(), a.getPricePoint(), b.getPriceMin(), b.getPricePoint());
        Double max = maxOf(a.getPriceMax(), a.getPricePoint(), b.getPriceMax(), b.getPricePoint());
        int countA = a.getBasedOnCount() != null ? a.getBasedOnCount() : 1;
        int countB = b.getBasedOnCount() != null ? b.getBasedOnCount() : 1;
        int total = countA + countB;
        double confA = a.getConfidence() != null ? a.getConfidence() : 0.5;
        double confB = b.getConfidence() != null ? b.getConfidence() : 0.5;
        double conf = (confA * countA + confB * countB) / total;

        PricingFactInput out = new PricingFactInput();
        out.setId(a.getId() != null ? a.getId() : b.getId());
        out.setProjectType(b.getProjectType() != null ? b.getProjectType() : a.getProjectType());
        out.setMaterial(b.getMaterial() != null ? b.getMaterial() : a.getMaterial());
        out.setUnit(b.getUnit() != null ? b.getUnit() : a.getUnit());
        out.setIncludesLabor(b.getIncludesLabor() != null ? b.getIncludesLabor() : a.getIncludesLabor());
        out.setCondition(b.getCondition() != null ? b.getCondition() : a.getCondition());
        out.setNotes(joinNotes(a.getNotes(), b.getNotes()));
        out.setSource(a.getSource() != null ? a.getSource() : b.getSource());
        out.setBasedOnCount(total);
        out.setConfidence(conf);
        out.setPriceMin(min);
        out.setPriceMax(max);
        out.setPricePoint(null);
        out.setObservedAt(Instant.now().toString());
        return out;
    }

    private static String joinNotes(String a, String b) {
        if (a == null || a.isBlank()) {
            return b;
        }
        if (b == null || b.isBlank()) {
            return a;
        }
        return a + " | " + b;
    }

    private static Double minOf(Double... xs) {
        Double m = null;
        for (Double x : xs) {
            if (x == null) {
                continue;
            }
            if (m == null || x < m) {
                m = x;
            }
        }
        return m;
    }

    private static Double maxOf(Double... xs) {
        Double m = null;
        for (Double x : xs) {
            if (x == null) {
                continue;
            }
            if (m == null || x > m) {
                m = x;
            }
        }
        return m;
    }

    private static PricingFactInput toInput(PricingFact f) {
        PricingFactInput i = new PricingFactInput();
        i.setId(f.getId());
        i.setProjectType(f.getProjectType());
        i.setMaterial(f.getMaterial());
        i.setUnit(f.getUnit());
        i.setPriceMin(f.getPriceMin());
        i.setPriceMax(f.getPriceMax());
        i.setPricePoint(f.getPricePoint());
        i.setIncludesLabor(f.getIncludesLabor());
        i.setCondition(f.getCondition());
        i.setNotes(f.getNotes());
        i.setSource(f.getSource());
        i.setConfidence(f.getConfidence());
        i.setBasedOnCount(f.getBasedOnCount());
        i.setObservedAt(f.getObservedAt());
        return i;
    }

    public Mono<PagedResult<TrainingData>> getTenantTrainingData(String tenantId, PageInput page, String search) {
        return paginate(
                trainingDataRepository.findAllByTenantId(tenantId)
                        .filter(td -> td.getProjectId() == null)
                        .filter(td -> matchesSearch(td, search)),
                page
        );
    }

    public Mono<PagedResult<TrainingData>> getProjectTrainingData(String tenantId, String projectId,
                                                                   PageInput page, String search) {
        return paginate(
                trainingDataRepository.findAllByTenantId(tenantId)
                        .filter(td -> projectId.equals(td.getProjectId()))
                        .filter(td -> matchesSearch(td, search)),
                page
        );
    }

    public Mono<TrainingData> getTrainingDataEntry(String id, String tenantId) {
        return trainingDataRepository.findByIdAndTenantId(id, tenantId);
    }

    public Mono<TrainingData> createTenantTrainingData(CreateTenantTrainingDataInput input) {
        if (!hasAnyTraining(input.getEntries(), input.getPricingFacts())) {
            return Mono.error(new IllegalArgumentException("Provide at least one entry or pricing fact."));
        }
        TrainingData entry = new TrainingData();
        entry.setTenantId(input.getTenantId());
        try {
            entry.setContent(serializeContent(input.getEntries(), input.getPricingFacts()));
        } catch (Exception e) {
            return Mono.error(new IllegalStateException("Failed to serialize training content", e));
        }
        entry.setDescription(input.getDescription());
        Instant now = Instant.now();
        entry.setUploadedAt(now);
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);

        return trainingDataRepository.save(entry);
    }

    public Mono<TrainingData> createProjectTrainingData(CreateProjectTrainingDataInput input) {
        if (!hasAnyTraining(input.getEntries(), input.getPricingFacts())) {
            return Mono.error(new IllegalArgumentException("Provide at least one entry or pricing fact."));
        }
        TrainingData entry = new TrainingData();
        entry.setTenantId(input.getTenantId());
        entry.setProjectId(input.getProjectId());
        try {
            entry.setContent(serializeContent(input.getEntries(), input.getPricingFacts()));
        } catch (Exception e) {
            return Mono.error(new IllegalStateException("Failed to serialize training content", e));
        }
        entry.setDescription(input.getDescription());
        Instant now = Instant.now();
        entry.setUploadedAt(now);
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);

        return trainingDataRepository.save(entry);
    }

    private static boolean hasAnyTraining(List<TrainingDataEntryInput> entries, List<PricingFactInput> facts) {
        boolean hasEntry = entries != null && entries.stream().anyMatch(e -> e != null && e.getKey() != null && !e.getKey().isBlank());
        boolean hasFact = facts != null && !facts.isEmpty();
        return hasEntry || hasFact;
    }

    public Mono<TrainingData> updateTrainingData(String id, String tenantId, UpdateTrainingDataInput input) {
        return trainingDataRepository.findByIdAndTenantId(id, tenantId)
                .flatMap(existing -> {
                    try {
                        List<TrainingDataEntryInput> entriesIn = input.getEntries();
                        List<PricingFactInput> factsIn = input.getPricingFacts();

                        if (entriesIn == null && factsIn == null) {
                            if (input.getDescription() != null) {
                                existing.setDescription(input.getDescription());
                            }
                            existing.setUpdatedAt(Instant.now());
                            return trainingDataRepository.save(existing);
                        }

                        TrainingContent tc = TrainingContent.fromJson(existing.getContent(), objectMapper);
                        List<TrainingDataEntryInput> resolvedEntries;
                        if (entriesIn != null) {
                            resolvedEntries = entriesIn;
                        } else {
                            resolvedEntries = tc.getEntries().stream()
                                    .map(m -> {
                                        TrainingDataEntryInput ei = new TrainingDataEntryInput();
                                        ei.setKey(m.get("key"));
                                        ei.setValue(m.get("value"));
                                        return ei;
                                    })
                                    .collect(Collectors.toList());
                        }
                        List<PricingFactInput> resolvedFacts;
                        if (factsIn != null) {
                            resolvedFacts = factsIn;
                        } else {
                            resolvedFacts = parsePricingFacts(existing.getContent()).stream()
                                    .map(TrainingDataService::toInput)
                                    .collect(Collectors.toList());
                        }
                        existing.setContent(serializeContent(resolvedEntries, resolvedFacts));
                        if (input.getDescription() != null) {
                            existing.setDescription(input.getDescription());
                        }
                        existing.setUpdatedAt(Instant.now());
                        return trainingDataRepository.save(existing);
                    } catch (Exception e) {
                        return Mono.error(new IllegalStateException("Failed to update training content", e));
                    }
                });
    }

    public Mono<Boolean> deleteTrainingData(String id, String tenantId) {
        return trainingDataRepository.findByIdAndTenantId(id, tenantId)
                .flatMap(existing -> trainingDataRepository.delete(existing).thenReturn(true));
    }

    private boolean matchesSearch(TrainingData td, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.toLowerCase();
        boolean contentMatch = td.getContent() != null && td.getContent().toLowerCase().contains(term);
        boolean descMatch = td.getDescription() != null && td.getDescription().toLowerCase().contains(term);
        boolean entriesMatch = parseEntries(td.getContent()).stream()
                .anyMatch(e -> (e.key() != null && e.key().toLowerCase().contains(term))
                        || (e.value() != null && e.value().toLowerCase().contains(term)));
        boolean factsMatch = parsePricingFacts(td.getContent()).stream()
                .anyMatch(f -> factMatches(f, term));
        return contentMatch || descMatch || entriesMatch || factsMatch;
    }

    private static boolean factMatches(PricingFact f, String term) {
        return contains(f.getProjectType(), term)
                || contains(f.getMaterial(), term)
                || contains(f.getNotes(), term)
                || contains(f.getCondition(), term);
    }

    private static boolean contains(String s, String term) {
        return s != null && s.toLowerCase().contains(term);
    }

    private Mono<PagedResult<TrainingData>> paginate(Flux<TrainingData> source, PageInput page) {
        int limit = PagedResult.effectiveLimit(page);
        int offset = PagedResult.effectiveOffset(page);

        return source.collectList()
                .map(all -> {
                    int total = all.size();
                    List<TrainingData> slice = all.stream()
                            .skip(offset)
                            .limit(limit)
                            .collect(Collectors.toList());
                    return new PagedResult<>(slice, total, limit, offset);
                });
    }
}
