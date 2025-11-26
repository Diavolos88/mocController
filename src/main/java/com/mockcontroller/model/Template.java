package com.mockcontroller.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public class Template {

    private String id;
    private String name;
    private String systemName;
    private JsonNode config;
    private String description;
    private Instant createdAt;

    public Template() {
    }

    public Template(String id, String name, String systemName, JsonNode config, String description, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.systemName = systemName;
        this.config = config;
        this.description = description;
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

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public JsonNode getConfig() {
        return config;
    }

    public void setConfig(JsonNode config) {
        this.config = config;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

