package com.mockcontroller.model;

import java.util.List;

public class ScenarioRequest {
    private String name;
    private String description;
    private List<ScenarioStepRequest> steps;

    public ScenarioRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ScenarioStepRequest> getSteps() {
        return steps;
    }

    public void setSteps(List<ScenarioStepRequest> steps) {
        this.steps = steps;
    }

    public static class ScenarioStepRequest {
        private String templateId;
        private long delayMs;
        private String scheduledTime; // Формат: "HH:mm:ss dd-MM-yyyy" или null

        public ScenarioStepRequest() {
        }

        public String getTemplateId() {
            return templateId;
        }

        public void setTemplateId(String templateId) {
            this.templateId = templateId;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }

        public String getScheduledTime() {
            return scheduledTime;
        }

        public void setScheduledTime(String scheduledTime) {
            this.scheduledTime = scheduledTime;
        }
    }
}

