package com.aether.app.trainingdata;

/**
 * A key-value pair for custom training data. Stored as JSON in TrainingData.content.
 */
public record TrainingDataEntry(String key, String value) {
}
