package com.mockcontroller.controller;

import com.mockcontroller.model.Scenario;
import com.mockcontroller.model.ScenarioRequest;
import com.mockcontroller.model.ScenarioStep;
import com.mockcontroller.service.ScenarioService;
import com.mockcontroller.service.ScheduledConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioApiController {

    private static final Logger logger = LoggerFactory.getLogger(ScenarioApiController.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy");

    private final ScenarioService scenarioService;
    private final ScheduledConfigService scheduledConfigService;

    public ScenarioApiController(ScenarioService scenarioService,
                                  ScheduledConfigService scheduledConfigService) {
        this.scenarioService = scenarioService;
        this.scheduledConfigService = scheduledConfigService;
    }

    @GetMapping
    public ResponseEntity<Collection<Scenario>> getAllScenarios() {
        try {
            return ResponseEntity.ok(scenarioService.findAll());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Scenario> getScenario(@PathVariable String id) {
        try {
            return scenarioService.findById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping
    public ResponseEntity<?> createScenario(@RequestBody ScenarioRequest request) {
        try {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Название сценария обязательно");
            }

            if (request.getSteps() == null || request.getSteps().isEmpty()) {
                return ResponseEntity.badRequest().body("Добавьте хотя бы один шаг в сценарий");
            }

            List<ScenarioStep> steps = convertSteps(request.getSteps());
            if (steps.isEmpty()) {
                return ResponseEntity.badRequest().body("Не удалось создать шаги сценария. Проверьте формат данных.");
            }

            Scenario scenario = scenarioService.createScenario(
                    request.getName(),
                    request.getDescription(),
                    steps
            );
            return ResponseEntity.ok(scenario);
        } catch (Exception e) {
            logger.error("Ошибка при создании сценария", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при создании сценария: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Scenario> updateScenario(
            @PathVariable String id,
            @RequestBody ScenarioRequest request) {
        try {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            List<ScenarioStep> steps = convertSteps(request.getSteps());
            Scenario scenario = scenarioService.updateScenario(
                    id,
                    request.getName(),
                    request.getDescription(),
                    steps
            );
            return ResponseEntity.ok(scenario);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScenario(@PathVariable String id) {
        try {
            scenarioService.deleteScenario(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<Void> executeScenario(@PathVariable String id) {
        try {
            Scenario scenario = scenarioService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Scenario not found"));

            LocalDateTime baseTime = LocalDateTime.now();
            long cumulativeDelay = 0;

            for (ScenarioStep step : scenario.getSteps()) {
                if (step.getTemplate() == null) {
                    continue;
                }

                LocalDateTime scheduledTime;
                if (step.getScheduledTime() != null) {
                    scheduledTime = LocalDateTime.ofInstant(step.getScheduledTime(), 
                            java.time.ZoneId.systemDefault());
                } else {
                    cumulativeDelay += step.getDelayMs();
                    scheduledTime = baseTime.plusSeconds(cumulativeDelay / 1000);
                }

                // Применяем шаблон через запланированное обновление
                scheduledConfigService.scheduleUpdate(
                        step.getTemplate().getSystemName(),
                        step.getTemplate().getConfig(),
                        scheduledTime,
                        "Сценарий: " + scenario.getName() + " (шаг " + step.getStepOrder() + ")"
                );
            }

            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private List<ScenarioStep> convertSteps(List<ScenarioRequest.ScenarioStepRequest> stepRequests) {
        if (stepRequests == null) {
            return new ArrayList<>();
        }

        List<ScenarioStep> steps = new ArrayList<>();
        for (ScenarioRequest.ScenarioStepRequest stepRequest : stepRequests) {
            if (stepRequest.getTemplateId() == null || stepRequest.getTemplateId().trim().isEmpty()) {
                continue; // Пропускаем шаги без templateId
            }
            
            ScenarioStep step = new ScenarioStep();
            step.setTemplateId(stepRequest.getTemplateId());
            step.setDelayMs(stepRequest.getDelayMs());

            if (stepRequest.getScheduledTime() != null && !stepRequest.getScheduledTime().isEmpty()) {
                try {
                    LocalDateTime scheduledTime = LocalDateTime.parse(stepRequest.getScheduledTime(), DATE_TIME_FORMATTER);
                    step.setScheduledTime(scheduledTime.atZone(java.time.ZoneId.systemDefault()).toInstant());
                } catch (Exception e) {
                    logger.warn("Ошибка парсинга времени для шага: {}", stepRequest.getScheduledTime(), e);
                    // Используем delayMs, если время не удалось распарсить
                }
            }

            steps.add(step);
        }
        return steps;
    }
}

