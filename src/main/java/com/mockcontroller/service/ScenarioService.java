package com.mockcontroller.service;

import com.mockcontroller.model.Scenario;
import com.mockcontroller.model.ScenarioStep;
import com.mockcontroller.model.entity.ScenarioEntity;
import com.mockcontroller.model.entity.ScenarioStepEntity;
import com.mockcontroller.repository.ScenarioRepository;
import com.mockcontroller.repository.ScenarioStepRepository;
import com.mockcontroller.repository.TemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ScenarioService {

    private final ScenarioRepository scenarioRepository;
    private final ScenarioStepRepository stepRepository;
    private final TemplateRepository templateRepository;
    private final ScenarioMapper mapper;
    private final TemplateMapper templateMapper;

    public ScenarioService(ScenarioRepository scenarioRepository, 
                           ScenarioStepRepository stepRepository,
                           TemplateRepository templateRepository,
                           ScenarioMapper mapper,
                           TemplateMapper templateMapper) {
        this.scenarioRepository = scenarioRepository;
        this.stepRepository = stepRepository;
        this.templateRepository = templateRepository;
        this.mapper = mapper;
        this.templateMapper = templateMapper;
    }

    public Collection<Scenario> findAll() {
        return scenarioRepository.findAll().stream()
                .map(entity -> {
                    Scenario scenario = mapper.toModel(entity);
                    loadSteps(scenario);
                    return scenario;
                })
                .collect(Collectors.toList());
    }

    public Optional<Scenario> findById(String id) {
        return scenarioRepository.findById(id).map(entity -> {
            Scenario scenario = mapper.toModel(entity);
            loadSteps(scenario);
            return scenario;
        });
    }

    public Optional<Scenario> findByGroupIdAndName(String groupId, String name) {
        return scenarioRepository.findByGroupIdAndName(groupId, name).map(entity -> {
            Scenario scenario = mapper.toModel(entity);
            loadSteps(scenario);
            return scenario;
        });
    }

    private void loadSteps(Scenario scenario) {
        List<ScenarioStep> steps = stepRepository.findByScenarioIdOrderByStepOrderAsc(scenario.getId())
                .stream()
                .map(stepEntity -> {
                    ScenarioStep step = mapper.toModel(stepEntity);
                    // Загружаем шаблон для шага
                    if (step.getTemplateId() != null && !step.getTemplateId().trim().isEmpty()) {
                        templateRepository.findById(step.getTemplateId())
                                .map(templateMapper::toModel)
                                .ifPresent(step::setTemplate);
                    }
                    return step;
                })
                .collect(Collectors.toList());
        scenario.setSteps(steps);
    }

    @Transactional
    public Scenario createScenario(String groupId, String name, String description, List<ScenarioStep> steps) {
        // Проверяем уникальность комбинации groupId + name
        if (scenarioRepository.existsByGroupIdAndName(groupId, name)) {
            throw new IllegalArgumentException("Сценарий с таким названием уже существует в этой группе");
        }
        
        ScenarioEntity entity = new ScenarioEntity();
        entity.setGroupId(groupId);
        entity.setName(name);
        entity.setDescription(description);
        entity.setCreatedAt(Instant.now());
        entity = scenarioRepository.save(entity);

        Scenario scenario = mapper.toModel(entity);
        
        // Сохраняем шаги
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                ScenarioStep step = steps.get(i);
                ScenarioStepEntity stepEntity = mapper.toEntity(step);
                stepEntity.setScenarioId(entity.getId());
                stepEntity.setStepOrder(i + 1);
                // Устанавливаем createdAt, если он не установлен
                if (stepEntity.getCreatedAt() == null) {
                    stepEntity.setCreatedAt(Instant.now());
                }
                stepRepository.save(stepEntity);
            }
        }
        
        loadSteps(scenario);
        return scenario;
    }

    @Transactional
    public Scenario updateScenario(String id, String groupId, String name, String description, List<ScenarioStep> steps) {
        ScenarioEntity entity = scenarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + id));

        // Проверяем уникальность комбинации groupId + name (исключая текущий сценарий)
        if (scenarioRepository.existsByGroupIdAndNameAndIdNot(groupId, name, id)) {
            throw new IllegalArgumentException("Сценарий с таким названием уже существует в этой группе");
        }

        entity.setGroupId(groupId);
        entity.setName(name);
        entity.setDescription(description);
        entity = scenarioRepository.save(entity);

        // Удаляем старые шаги
        stepRepository.deleteByScenarioId(id);

        // Сохраняем новые шаги
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                ScenarioStep step = steps.get(i);
                ScenarioStepEntity stepEntity = mapper.toEntity(step);
                stepEntity.setScenarioId(id);
                stepEntity.setStepOrder(i + 1);
                // Устанавливаем createdAt, если он не установлен
                if (stepEntity.getCreatedAt() == null) {
                    stepEntity.setCreatedAt(Instant.now());
                }
                stepRepository.save(stepEntity);
            }
        }

        Scenario updatedScenario = mapper.toModel(entity);
        loadSteps(updatedScenario);
        return updatedScenario;
    }

    @Transactional
    public void deleteScenario(String id) {
        if (!scenarioRepository.existsById(id)) {
            throw new IllegalArgumentException("Scenario not found: " + id);
        }
        stepRepository.deleteByScenarioId(id);
        scenarioRepository.deleteById(id);
    }
}

