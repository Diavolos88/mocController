package com.mockcontroller.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class ConfigResponse {

    @JsonProperty(value = "SystemName")
    private String systemName;

    @JsonProperty(value = "version")
    private String version;

    @JsonProperty(value = "config")
    private JsonNode config;

    @JsonProperty(value = "updatedAt")
    private String updatedAt;

    public ConfigResponse() {
    }

    public ConfigResponse(String systemName, String version, JsonNode config, String updatedAt) {
        this.systemName = systemName;
        this.version = version;
        this.config = config;
        this.updatedAt = updatedAt;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public JsonNode getConfig() {
        return config;
    }

    public void setConfig(JsonNode config) {
        this.config = config;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}

