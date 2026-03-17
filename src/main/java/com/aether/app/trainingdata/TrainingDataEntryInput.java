package com.aether.app.trainingdata;

/**
 * Input for a single key-value pair when creating/updating training data.
 */
public class TrainingDataEntryInput {

    private String key;
    private String value;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
