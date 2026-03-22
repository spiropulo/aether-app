package com.aether.app.trainingdata;

import com.aether.app.estimate.PricingParseClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Proxies natural-language pricing text to the AI service and returns structured JSON facts.
 */
@Tag(name = "Training", description = "Parse natural language into structured pricing facts")
@RestController
@RequestMapping("/api/v1/training")
public class TrainingParseRestController {

    private static final Logger log = LoggerFactory.getLogger(TrainingParseRestController.class);

    private final PricingParseClient pricingParseClient;
    private final ObjectMapper objectMapper;

    public TrainingParseRestController(PricingParseClient pricingParseClient, ObjectMapper objectMapper) {
        this.pricingParseClient = pricingParseClient;
        this.objectMapper = objectMapper;
    }

    public record ParseRequestBody(String text, String projectTypeHint, String unitHint) {}

    @Operation(summary = "Parse pricing text into structured facts (AI)")
    @PostMapping(value = "/parse-pricing", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> parsePricing(@RequestBody ParseRequestBody body) {
        if (body == null || body.text() == null || body.text().isBlank()) {
            Map<String, Object> bad = new LinkedHashMap<>();
            bad.put("detail", "text is required");
            return Mono.just(ResponseEntity.badRequest().body(bad));
        }
        if (!pricingParseClient.isConfigured()) {
            Map<String, Object> cfg = new LinkedHashMap<>();
            cfg.put("detail", "Pricing parse is not configured. Set aether.agent.pricing-parse-url to the Aether AI service.");
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(cfg));
        }
        return pricingParseClient.parsePricingText(body.text(), body.projectTypeHint(), body.unitHint())
                .map(json -> {
                    try {
                        JsonNode root = objectMapper.readTree(json);
                        JsonNode facts = root.get("facts");
                        List<Map<String, Object>> out = new ArrayList<>();
                        if (facts != null && facts.isArray()) {
                            for (JsonNode f : facts) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> m = objectMapper.convertValue(f, Map.class);
                                out.add(m);
                            }
                        }
                        Map<String, Object> response = new LinkedHashMap<>();
                        response.put("facts", out);
                        if (root.has("warnings") && root.get("warnings").isArray()) {
                            response.put("warnings", objectMapper.convertValue(root.get("warnings"), List.class));
                        }
                        return ResponseEntity.ok(response);
                    } catch (Exception e) {
                        log.error("Failed to parse pricing response", e);
                        Map<String, Object> err = new LinkedHashMap<>();
                        err.put("detail", "Invalid response from pricing parse service");
                        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(err);
                    }
                })
                .onErrorResume(e -> {
                    String msg = e.getMessage() != null ? e.getMessage() : "Pricing parse failed";
                    Map<String, Object> errBody = new LinkedHashMap<>();
                    errBody.put("detail", msg);
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errBody));
                });
    }
}
