package com.mockcontroller.model;

import java.time.Instant;

public class ScenarioStep {

    private String id;
    private String scenarioId;
    private String templateId;
    private Template template; // Загружается отдельно
    private int stepOrder;
    private long delayMs;
    private Instant scheduledTime;
    private Instant createdAt;
    private String comment;

    public ScenarioStep() {
    }

    public ScenarioStep(String id, String scenarioId, String templateId, int stepOrder, long delayMs, Instant scheduledTime, Instant createdAt) {
        this.id = id;
        this.scenarioId = scenarioId;
        this.templateId = templateId;
        this.stepOrder = stepOrder;
        this.delayMs = delayMs;
        this.scheduledTime = scheduledTime;
        this.createdAt = createdAt;
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

    public Template getTemplate() {
        return template;
    }

    public void setTemplate(Template template) {
        this.template = template;
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

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}

