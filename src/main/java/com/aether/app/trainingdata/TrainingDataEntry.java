package com.aether.app.trainingdata;

/**
 * A key-value pair for custom training data. Stored as JSON in TrainingData.content.
 * {@code key} is often a human-readable description of what {@code value} applies to (for semantic
 * matching to offer line items); it may also be a short technical identifier from presets.
 */
public record TrainingDataEntry(String key, String value) {
}
