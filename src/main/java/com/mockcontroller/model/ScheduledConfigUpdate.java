package com.mockcontroller.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.UUID;

public class ScheduledConfigUpdate {
    private String id;
    private String systemName;
    private JsonNode newConfig;
    private LocalDateTime scheduledTime;
    private LocalDateTime createdAt;
    private String comment;

    public ScheduledConfigUpdate() {
    }

    public ScheduledConfigUpdate(String systemName, JsonNode newConfig, LocalDateTime scheduledTime) {
        this.id = UUID.randomUUID().toString();
        this.systemName = systemName;
        this.newConfig = newConfig;
        this.scheduledTime = scheduledTime;
        this.createdAt = LocalDateTime.now();
    }

    public ScheduledConfigUpdate(String systemName, JsonNode newConfig, LocalDateTime scheduledTime, String comment) {
        this.id = UUID.randomUUID().toString();
        this.systemName = systemName;
        this.newConfig = newConfig;
        this.scheduledTime = scheduledTime;
        this.createdAt = LocalDateTime.now();
        this.comment = comment;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public JsonNode getNewConfig() {
        return newConfig;
    }

    public void setNewConfig(JsonNode newConfig) {
        this.newConfig = newConfig;
    }

    public LocalDateTime getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(LocalDateTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}


