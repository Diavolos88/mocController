package com.mockcontroller.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ScheduleUpdateResponse {

    @JsonProperty(value = "success")
    private boolean success;

    @JsonProperty(value = "message")
    private String message;

    @JsonProperty(value = "updateId")
    private String updateId;

    @JsonProperty(value = "scheduledTime")
    private String scheduledTime;

    public ScheduleUpdateResponse() {
    }

    public ScheduleUpdateResponse(boolean success, String message, String updateId, String scheduledTime) {
        this.success = success;
        this.message = message;
        this.updateId = updateId;
        this.scheduledTime = scheduledTime;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUpdateId() {
        return updateId;
    }

    public void setUpdateId(String updateId) {
        this.updateId = updateId;
    }

    public String getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(String scheduledTime) {
        this.scheduledTime = scheduledTime;
    }
}

