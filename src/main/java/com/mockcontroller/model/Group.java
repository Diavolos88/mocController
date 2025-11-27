package com.mockcontroller.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Group {

    private String id;
    private String name;
    private String description;
    private List<String> systemNames;
    private Instant createdAt;

    public Group() {
        this.systemNames = new ArrayList<>();
    }

    public Group(String id, String name, String description, List<String> systemNames, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.systemNames = systemNames != null ? systemNames : new ArrayList<>();
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

    public List<String> getSystemNames() {
        return systemNames;
    }

    public void setSystemNames(List<String> systemNames) {
        this.systemNames = systemNames;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

