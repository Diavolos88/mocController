package com.mockcontroller.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class ScheduleUpdateRequest {

    @JsonProperty(value = "SystemName", required = true)
    private String systemName;

    @JsonProperty(value = "scheduledTime", required = true)
    private String scheduledTime; // Формат: "HH:mm:ss dd-MM-yyyy"

    @JsonProperty(value = "config", required = true)
    private JsonNode config;

    @JsonProperty(value = "comment")
    private String comment;

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(String scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public JsonNode getConfig() {
        return config;
    }

    public void setConfig(JsonNode config) {
        this.config = config;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}

