package com.mockcontroller.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "scenario_steps")
public class ScenarioStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "scenario_id", nullable = false, length = 36)
    private String scenarioId;

    @Column(name = "template_id", nullable = false, length = 36)
    private String templateId;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(name = "delay_ms", nullable = false)
    private long delayMs;

    @Column(name = "scheduled_time")
    private Instant scheduledTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ScenarioStepEntity() {
        this.createdAt = Instant.now();
        this.delayMs = 0;
    }

    public ScenarioStepEntity(String scenarioId, String templateId, int stepOrder, long delayMs) {
        this();
        this.scenarioId = scenarioId;
        this.templateId = templateId;
        this.stepOrder = stepOrder;
        this.delayMs = delayMs;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(String scenarioId) {
        this.scenarioId = scenarioId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(int stepOrder) {
        this.stepOrder = stepOrder;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    public Instant getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(Instant scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

