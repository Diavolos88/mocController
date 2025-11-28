package com.mockcontroller.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mock_instances", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"system_name", "instance_id"}))
public class MockInstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "system_name", nullable = false, length = 255)
    private String systemName;

    @Column(name = "instance_id", length = 255)
    private String instanceId;

    @Column(name = "last_healthcheck_time", nullable = false)
    private Instant lastHealthcheckTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public MockInstanceEntity() {
        this.createdAt = Instant.now();
        this.lastHealthcheckTime = Instant.now();
    }

    public MockInstanceEntity(String systemName, String instanceId) {
        this();
        this.systemName = systemName;
        this.instanceId = instanceId != null ? instanceId : "default";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId != null ? instanceId : "default";
    }

    public Instant getLastHealthcheckTime() {
        return lastHealthcheckTime;
    }

    public void setLastHealthcheckTime(Instant lastHealthcheckTime) {
        this.lastHealthcheckTime = lastHealthcheckTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

