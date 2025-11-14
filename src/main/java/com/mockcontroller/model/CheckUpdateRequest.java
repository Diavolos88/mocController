package com.mockcontroller.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class CheckUpdateRequest {

    @JsonProperty(value = "SystemName", required = true)
    private String systemName;

    @JsonProperty(value = "version", required = true)
    private String version;

    @JsonProperty(value = "config", required = true)
    private JsonNode config;

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
}

