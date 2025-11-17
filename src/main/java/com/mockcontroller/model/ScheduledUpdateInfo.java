package com.mockcontroller.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ScheduledUpdateInfo {

    @JsonProperty(value = "id")
    private String id;

    @JsonProperty(value = "systemName")
    private String systemName;

    @JsonProperty(value = "scheduledTime")
    private String scheduledTime;

    @JsonProperty(value = "comment")
    private String comment;

    @JsonProperty(value = "createdAt")
    private String createdAt;

    public ScheduledUpdateInfo() {
    }

    public ScheduledUpdateInfo(ScheduledConfigUpdate update, String formattedScheduledTime, String formattedCreatedAt) {
        this.id = update.getId();
        this.systemName = update.getSystemName();
        this.scheduledTime = formattedScheduledTime;
        this.comment = update.getComment();
        this.createdAt = formattedCreatedAt;
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

    public String getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(String scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}

