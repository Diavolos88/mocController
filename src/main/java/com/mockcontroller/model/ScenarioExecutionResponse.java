package com.mockcontroller.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ScenarioExecutionResponse {
    
    @JsonProperty("success")
    private boolean success;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("scheduledCount")
    private int scheduledCount;

    public ScenarioExecutionResponse() {
    }

    public ScenarioExecutionResponse(boolean success, String message, int scheduledCount) {
        this.success = success;
        this.message = message;
        this.scheduledCount = scheduledCount;
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

    public int getScheduledCount() {
        return scheduledCount;
    }

    public void setScheduledCount(int scheduledCount) {
        this.scheduledCount = scheduledCount;
    }
}

