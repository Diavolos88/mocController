package com.mockcontroller.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CheckUpdateResponse {

    @JsonProperty(value = "needUpdate")
    private boolean needUpdate;

    @JsonProperty(value = "currentVersion")
    private String currentVersion;

    public CheckUpdateResponse() {
    }

    public CheckUpdateResponse(boolean needUpdate, String currentVersion) {
        this.needUpdate = needUpdate;
        this.currentVersion = currentVersion;
    }

    public boolean isNeedUpdate() {
        return needUpdate;
    }

    public void setNeedUpdate(boolean needUpdate) {
        this.needUpdate = needUpdate;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }
}

