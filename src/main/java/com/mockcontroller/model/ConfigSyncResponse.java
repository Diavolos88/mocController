package com.mockcontroller.model;

public class ConfigSyncResponse {

    private SyncStatus status;
    private String message;
    private String currentVersion;

    public ConfigSyncResponse() {
    }

    public ConfigSyncResponse(SyncStatus status, String message, String currentVersion) {
        this.status = status;
        this.message = message;
        this.currentVersion = currentVersion;
    }

    public SyncStatus getStatus() {
        return status;
    }

    public void setStatus(SyncStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public enum SyncStatus {
        START_REGISTERED,
        UPDATED_START_CONFIG,
        UPDATE_AVAILABLE,
        NO_CHANGES
    }
}
