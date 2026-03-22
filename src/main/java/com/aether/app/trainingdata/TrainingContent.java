package com.aether.app.trainingdata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JSON shape stored in {@link TrainingData#content}:
 * <ul>
 *   <li><b>Legacy</b>: JSON array of {@code { "key", "value" }} only.</li>
 *   <li><b>v2</b>: {@code { "version": 2, "entries": [...], "pricingFacts": [...] }}</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class TrainingContent {

    public static final int VERSION = 2;

    private Integer version;
    private List<Map<String, String>> entries;
    private List<Map<String, Object>> pricingFacts;

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public List<Map<String, String>> getEntries() {
        return entries;
    }

    public void setEntries(List<Map<String, String>> entries) {
        this.entries = entries;
    }

    public List<Map<String, Object>> getPricingFacts() {
        return pricingFacts;
    }

    public void setPricingFacts(List<Map<String, Object>> pricingFacts) {
        this.pricingFacts = pricingFacts;
    }

    private static final TypeReference<List<Map<String, String>>> ENTRIES_REF = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> FACTS_REF = new TypeReference<>() {};

    /**
     * Parse raw content: legacy array or v2 object.
     */
    public static TrainingContent fromJson(String content, ObjectMapper objectMapper) {
        TrainingContent out = new TrainingContent();
        out.setEntries(new ArrayList<>());
        out.setPricingFacts(new ArrayList<>());
        if (content == null || content.isBlank()) {
            return out;
        }
        String trimmed = content.trim();
        try {
            if (trimmed.startsWith("[")) {
                List<Map<String, String>> raw = objectMapper.readValue(trimmed, ENTRIES_REF);
                if (raw != null) {
                    out.setEntries(new ArrayList<>(raw));
                }
                return out;
            }
            Map<String, Object> root = objectMapper.readValue(trimmed, new TypeReference<>() {});
            if (root == null) {
                return out;
            }
            Object v = root.get("version");
            if (v instanceof Number) {
                out.setVersion(((Number) v).intValue());
            }
            Object ent = root.get("entries");
            if (ent instanceof List<?> list) {
                List<Map<String, String>> parsed = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        String k = m.get("key") != null ? String.valueOf(m.get("key")) : "";
                        String val = m.get("value") != null ? String.valueOf(m.get("value")) : "";
                        parsed.add(Map.of("key", k, "value", val));
                    }
                }
                out.setEntries(parsed);
            }
            Object facts = root.get("pricingFacts");
            if (facts instanceof List<?> list) {
                List<Map<String, Object>> parsed = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> cast = (Map<String, Object>) m;
                        parsed.add(cast);
                    }
                }
                out.setPricingFacts(parsed);
            }
            return out;
        } catch (Exception e) {
            out.getEntries().clear();
            out.getEntries().add(Map.of("key", "content", "value", content));
            return out;
        }
    }

    public static String toJson(TrainingContent c, ObjectMapper objectMapper) throws Exception {
        if (c == null) {
            return "[]";
        }
        boolean hasFacts = c.getPricingFacts() != null && !c.getPricingFacts().isEmpty();
        boolean onlyLegacyEntries = !hasFacts
                && (c.getVersion() == null || c.getVersion() < VERSION)
                && c.getEntries() != null
                && !c.getEntries().isEmpty();
        if (onlyLegacyEntries && (c.getVersion() == null || c.getVersion() < VERSION)) {
            return objectMapper.writeValueAsString(c.getEntries());
        }
        TrainingContent v2 = new TrainingContent();
        v2.setVersion(VERSION);
        v2.setEntries(c.getEntries() != null ? c.getEntries() : List.of());
        v2.setPricingFacts(c.getPricingFacts() != null ? c.getPricingFacts() : List.of());
        return objectMapper.writeValueAsString(v2);
    }

    public static Map<String, Object> factToMap(PricingFact f, ObjectMapper objectMapper) {
        return objectMapper.convertValue(f, new TypeReference<>() {});
    }

    public static PricingFact mapToFact(Map<String, Object> m, ObjectMapper objectMapper) {
        return objectMapper.convertValue(m, PricingFact.class);
    }

    public static String newFactId() {
        return UUID.randomUUID().toString();
    }
}
