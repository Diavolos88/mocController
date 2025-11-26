package com.mockcontroller.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "templates")
public class TemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "system_name", nullable = false, length = 255)
    private String systemName;

    @Column(name = "config", columnDefinition = "TEXT", nullable = false)
    private String configJson;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public TemplateEntity() {
        this.createdAt = Instant.now();
    }

    public TemplateEntity(String name, String systemName, String configJson, String description) {
        this();
        this.name = name;
        this.systemName = systemName;
        this.configJson = configJson;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

