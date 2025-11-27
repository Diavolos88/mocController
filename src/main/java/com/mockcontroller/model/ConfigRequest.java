package com.mockcontroller.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class ConfigRequest {

    @JsonProperty(value = "SystemName", required = true)
    private String systemName;

    @JsonProperty(value = "config", required = true)
    private JsonNode config;

    @JsonProperty(value = "version", required = false)
    private String version;

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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
