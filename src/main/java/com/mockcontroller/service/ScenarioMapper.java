package com.mockcontroller.service;

import com.mockcontroller.model.Scenario;
import com.mockcontroller.model.ScenarioStep;
import com.mockcontroller.model.entity.ScenarioEntity;
import com.mockcontroller.model.entity.ScenarioStepEntity;
import org.springframework.stereotype.Component;


@Component
public class ScenarioMapper {

    public Scenario toModel(ScenarioEntity entity) {
        if (entity == null) {
            return null;
        }
        
        return new Scenario(
            entity.getId(),
            entity.getGroupId(),
            entity.getName(),
            entity.getDescription(),
            null, // steps будут загружены отдельно
            entity.getCreatedAt()
        );
    }

    public ScenarioEntity toEntity(Scenario scenario) {
        if (scenario == null) {
            return null;
        }
        
        ScenarioEntity entity = new ScenarioEntity();
        if (scenario.getId() != null && !scenario.getId().isEmpty()) {
            entity.setId(scenario.getId());
        }
        entity.setGroupId(scenario.getGroupId());
        entity.setName(scenario.getName());
        entity.setDescription(scenario.getDescription());
        if (scenario.getCreatedAt() != null) {
            entity.setCreatedAt(scenario.getCreatedAt());
        }
        
        return entity;
    }

    public ScenarioStep toModel(ScenarioStepEntity entity) {
        if (entity == null) {
            return null;
        }
        
        ScenarioStep step = new ScenarioStep(
            entity.getId(),
            entity.getScenarioId(),
            entity.getTemplateId(),
            entity.getStepOrder(),
            entity.getDelayMs(),
            entity.getScheduledTime(),
            entity.getCreatedAt()
        );
        step.setComment(entity.getComment());
        return step;
    }

    public ScenarioStepEntity toEntity(ScenarioStep step) {
        if (step == null) {
            return null;
        }
        
        ScenarioStepEntity entity = new ScenarioStepEntity();
        entity.setId(step.getId());
        entity.setScenarioId(step.getScenarioId());
        entity.setTemplateId(step.getTemplateId());
        entity.setStepOrder(step.getStepOrder());
        entity.setDelayMs(step.getDelayMs());
        entity.setScheduledTime(step.getScheduledTime());
        entity.setCreatedAt(step.getCreatedAt());
        entity.setComment(step.getComment());
        
        return entity;
    }
}

