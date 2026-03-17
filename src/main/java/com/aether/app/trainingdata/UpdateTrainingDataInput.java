package com.aether.app.trainingdata;

import java.util.List;

public class UpdateTrainingDataInput {

    private List<TrainingDataEntryInput> entries;
    private String description;

    public List<TrainingDataEntryInput> getEntries() {
        return entries;
    }

    public void setEntries(List<TrainingDataEntryInput> entries) {
        this.entries = entries;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
