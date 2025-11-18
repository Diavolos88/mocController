package com.mockcontroller.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "stored_configs")
public class StoredConfigEntity {

    @Id
    @Column(name = "system_name", unique = true, nullable = false, length = 255)
    private String systemName;

    @Column(name = "start_config", columnDefinition = "TEXT", nullable = false)
    private String startConfigJson;

    @Column(name = "current_config", columnDefinition = "TEXT", nullable = false)
    private String currentConfigJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "version", nullable = false)
    private int version = 1;

    public StoredConfigEntity() {
    }

    public StoredConfigEntity(String systemName, String startConfigJson, String currentConfigJson, Instant updatedAt) {
        this.systemName = systemName;
        this.startConfigJson = startConfigJson;
        this.currentConfigJson = currentConfigJson;
        this.updatedAt = updatedAt;
        this.version = 1;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getStartConfigJson() {
        return startConfigJson;
    }

    public void setStartConfigJson(String startConfigJson) {
        this.startConfigJson = startConfigJson;
    }

    public String getCurrentConfigJson() {
        return currentConfigJson;
    }

    public void setCurrentConfigJson(String currentConfigJson) {
        this.currentConfigJson = currentConfigJson;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}

