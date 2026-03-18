package com.aether.app.estimate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds a human-readable report for a pricing run.
 */
public final class PricingRunReportBuilder {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.US);

    private PricingRunReportBuilder() {
    }

    /**
     * Build a concise, human-readable report from pricing run data.
     */
    public static String build(String agentReport, String offersSnapshotJson,
                               Integer toolCallsMade, Instant runAt, ObjectMapper mapper) {
        StringBuilder sb = new StringBuilder();

        // Header
        String dateStr = runAt != null
                ? runAt.atZone(ZoneId.systemDefault()).format(DATE_FMT)
                : "Unknown date";
        sb.append("Pricing Run — ").append(dateStr).append("\n\n");

        // Summary from offers snapshot
        int offerCount = 0;
        double projectTotal = 0.0;
        List<String> offerLines = new ArrayList<>();

        if (offersSnapshotJson != null && !offersSnapshotJson.isBlank()) {
            try {
                JsonNode root = mapper.readTree(offersSnapshotJson);
                JsonNode offers = root.get("offers");
                JsonNode totalNode = root.get("projectTotal");
                if (totalNode != null && totalNode.isNumber()) {
                    projectTotal = totalNode.asDouble();
                }
                if (offers != null && offers.isArray()) {
                    offerCount = offers.size();
                    for (JsonNode o : offers) {
                        String name = o.has("name") ? o.get("name").asText() : "Offer";
                        Number qty = o.has("quantity") && !o.get("quantity").isNull()
                                ? o.get("quantity").asDouble() : 0;
                        Number uc = o.has("unitCost") && !o.get("unitCost").isNull()
                                ? o.get("unitCost").asDouble() : 0;
                        Number tot = o.has("total") && !o.get("total").isNull()
                                ? o.get("total").asDouble() : qty.doubleValue() * uc.doubleValue();
                        String uom = o.has("uom") && !o.get("uom").isNull()
                                ? o.get("uom").asText().trim() : "";
                        String uomPart = uom.isBlank() ? "" : "/" + uom;
                        offerLines.add(String.format("• %s: %s × $%.2f%s = $%.2f",
                                name, formatNum(qty), uc.doubleValue(), uomPart, tot.doubleValue()));
                    }
                }
            } catch (Exception ignored) {
                // fallback
            }
        }

        sb.append("Summary: Priced ").append(offerCount).append(" offer")
                .append(offerCount != 1 ? "s" : "")
                .append(". Project total: $").append(String.format("%.2f", projectTotal))
                .append(".\n\n");

        if (!offerLines.isEmpty()) {
            sb.append("Pricing decisions:\n");
            for (String line : offerLines) {
                sb.append(line).append("\n");
            }
            sb.append("\n");
        }

        // Agent explanation (keep short)
        if (agentReport != null && !agentReport.isBlank()) {
            String trimmed = agentReport.trim();
            if (trimmed.length() > 500) {
                trimmed = trimmed.substring(0, 500).trim() + "...";
            }
            sb.append("How it was decided:\n").append(trimmed);
        }

        return sb.toString().trim();
    }

    private static String formatNum(Number n) {
        if (n == null) return "0";
        double d = n.doubleValue();
        if (d == (long) d) return String.valueOf((long) d);
        return String.valueOf(d);
    }
}
