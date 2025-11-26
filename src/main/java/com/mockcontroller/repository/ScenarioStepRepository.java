package com.mockcontroller.repository;

import com.mockcontroller.model.entity.ScenarioStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScenarioStepRepository extends JpaRepository<ScenarioStepEntity, String> {
    
    List<ScenarioStepEntity> findByScenarioIdOrderByStepOrderAsc(String scenarioId);
    
    void deleteByScenarioId(String scenarioId);
    
    void deleteByTemplateId(String templateId);
}

