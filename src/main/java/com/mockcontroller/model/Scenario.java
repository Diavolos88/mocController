package com.mockcontroller.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Scenario {

    private String id;
    private String name;
    private String description;
    private List<ScenarioStep> steps;
    private Instant createdAt;

    public Scenario() {
        this.steps = new ArrayList<>();
    }

    public Scenario(String id, String name, String description, List<ScenarioStep> steps, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.steps = steps != null ? steps : new ArrayList<>();
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ScenarioStep> getSteps() {
        return steps;
    }

    public void setSteps(List<ScenarioStep> steps) {
        this.steps = steps;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

