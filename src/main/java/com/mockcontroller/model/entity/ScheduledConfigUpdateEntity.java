package com.mockcontroller.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "scheduled_config_updates")
public class ScheduledConfigUpdateEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "system_name", nullable = false, length = 255)
    private String systemName;

    @Column(name = "new_config", columnDefinition = "TEXT", nullable = false)
    private String newConfigJson;

    @Column(name = "scheduled_time", nullable = false)
    private LocalDateTime scheduledTime;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "applied", nullable = false)
    private Boolean applied = false;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public ScheduledConfigUpdateEntity() {
    }

    public ScheduledConfigUpdateEntity(String systemName, String newConfigJson, LocalDateTime scheduledTime) {
        this.id = UUID.randomUUID().toString();
        this.systemName = systemName;
        this.newConfigJson = newConfigJson;
        this.scheduledTime = scheduledTime;
        this.createdAt = LocalDateTime.now();
        this.applied = false;
    }

    public ScheduledConfigUpdateEntity(String systemName, String newConfigJson, LocalDateTime scheduledTime, String comment) {
        this.id = UUID.randomUUID().toString();
        this.systemName = systemName;
        this.newConfigJson = newConfigJson;
        this.scheduledTime = scheduledTime;
        this.createdAt = LocalDateTime.now();
        this.comment = comment;
        this.applied = false;
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

    public String getNewConfigJson() {
        return newConfigJson;
    }

    public void setNewConfigJson(String newConfigJson) {
        this.newConfigJson = newConfigJson;
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

    public Boolean getApplied() {
        return applied != null ? applied : false;
    }

    public void setApplied(Boolean applied) {
        this.applied = applied != null ? applied : false;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }
}

