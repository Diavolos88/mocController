package com.mockcontroller.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public class StoredConfig {

    private String systemName;
    private JsonNode startConfig;
    private JsonNode currentConfig;
    private Instant updatedAt;
    private int version;

    public StoredConfig() {
        this.version = 1;
    }

    public StoredConfig(String systemName, JsonNode startConfig, JsonNode currentConfig, Instant updatedAt) {
        this.systemName = systemName;
        this.startConfig = startConfig;
        this.currentConfig = currentConfig;
        this.updatedAt = updatedAt;
        this.version = 1;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public JsonNode getStartConfig() {
        return startConfig;
    }

    public void setStartConfig(JsonNode startConfig) {
        this.startConfig = startConfig;
    }

    public JsonNode getCurrentConfig() {
        return currentConfig;
    }

    public void setCurrentConfig(JsonNode currentConfig) {
        this.currentConfig = currentConfig;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
