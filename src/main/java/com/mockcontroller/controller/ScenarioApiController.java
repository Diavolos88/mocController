package com.mockcontroller.controller;

import com.mockcontroller.model.Scenario;
import com.mockcontroller.model.ScenarioRequest;
import com.mockcontroller.model.ScenarioStep;
import com.mockcontroller.service.GroupService;
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
    private final GroupService groupService;

    public ScenarioApiController(ScenarioService scenarioService,
                                  ScheduledConfigService scheduledConfigService,
                                  GroupService groupService) {
        this.scenarioService = scenarioService;
        this.scheduledConfigService = scheduledConfigService;
        this.groupService = groupService;
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

            if (request.getGroupId() == null || request.getGroupId().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Группа обязательна");
            }

            Scenario scenario = scenarioService.createScenario(
                    request.getGroupId(),
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
    public ResponseEntity<?> updateScenario(
            @PathVariable String id,
            @RequestBody ScenarioRequest request) {
        try {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Название сценария обязательно");
            }

            if (request.getSteps() == null || request.getSteps().isEmpty()) {
                return ResponseEntity.badRequest().body("Добавьте хотя бы один шаг в сценарий");
            }

            if (request.getGroupId() == null || request.getGroupId().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Группа обязательна");
            }

            List<ScenarioStep> steps = convertSteps(request.getSteps());
            if (steps.isEmpty()) {
                return ResponseEntity.badRequest().body("Не удалось создать шаги сценария. Проверьте формат данных.");
            }

            Scenario scenario = scenarioService.updateScenario(
                    id,
                    request.getGroupId(),
                    request.getName(),
                    request.getDescription(),
                    steps
            );
            return ResponseEntity.ok(scenario);
        } catch (IllegalArgumentException e) {
            logger.error("Сценарий не найден: {}", id, e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Ошибка при обновлении сценария", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при обновлении сценария: " + e.getMessage());
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

    @GetMapping("/execute")
    public ResponseEntity<?> executeScenarioByGroupAndName(
            @RequestParam String group,
            @RequestParam String name,
            @RequestParam String startTime) {
        try {
            // Парсим время начала
            LocalDateTime startDateTime;
            try {
                startDateTime = LocalDateTime.parse(startTime, DATE_TIME_FORMATTER);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Неверный формат времени. Используйте: HH:mm:ss dd-MM-yyyy");
            }

            // Проверяем, что время не в прошлом
            if (startDateTime.isBefore(LocalDateTime.now())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Время начала не может быть в прошлом");
            }

            // Ищем группу по ID или названию
            String groupId = group;
            // Если group не похож на UUID, пытаемся найти группу по названию
            if (!group.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
                // Это название группы, нужно найти ID
                groupId = groupService.findAll().stream()
                        .filter(g -> g.getName().equalsIgnoreCase(group))
                        .findFirst()
                        .map(g -> g.getId())
                        .orElse(null);
                
                if (groupId == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("Группа с названием '" + group + "' не найдена. Используйте /api/groups для получения списка групп.");
                }
            }

            // Ищем сценарий по groupId и name
            Scenario scenario = scenarioService.findByGroupIdAndName(groupId, name)
                    .orElseThrow(() -> new IllegalArgumentException("Сценарий не найден"));

            // Применяем шаги сценария относительно времени начала
            for (ScenarioStep step : scenario.getSteps()) {
                if (step.getTemplate() == null) {
                    continue;
                }

                LocalDateTime scheduledTime;

                if (step.getScheduledTime() != null) {
                    // Если у шага есть scheduledTime, извлекаем относительное время (HH:mm)
                    LocalDateTime stepTime = LocalDateTime.ofInstant(step.getScheduledTime(), 
                            java.time.ZoneId.systemDefault());
                    int hours = stepTime.getHour();
                    int minutes = stepTime.getMinute();
                    // Вычисляем абсолютное время: startDateTime + относительное время
                    scheduledTime = startDateTime.plusHours(hours).plusMinutes(minutes);
                } else {
                    // Используем delayMs относительно startDateTime
                    long delaySeconds = step.getDelayMs() / 1000;
                    scheduledTime = startDateTime.plusSeconds(delaySeconds);
                }

                // Убеждаемся, что время не в прошлом
                if (scheduledTime.isBefore(LocalDateTime.now())) {
                    scheduledTime = LocalDateTime.now().plusSeconds(1);
                }

                // Применяем шаблон через запланированное обновление
                scheduledConfigService.scheduleUpdate(
                        step.getTemplate().getSystemName(),
                        step.getTemplate().getConfig(),
                        scheduledTime,
                        "Сценарий: " + scenario.getName() + " (шаг " + step.getStepOrder() + ")"
                );
            }

            return ResponseEntity.ok("Сценарий '" + scenario.getName() + "' успешно запланирован на " + startTime);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Ошибка при выполнении сценария", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при выполнении сценария: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<Void> executeScenario(@PathVariable String id) {
        try {
            Scenario scenario = scenarioService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Scenario not found"));

            LocalDateTime baseTime = LocalDateTime.now();

            for (ScenarioStep step : scenario.getSteps()) {
                if (step.getTemplate() == null) {
                    continue;
                }

                LocalDateTime scheduledTime;
                if (step.getScheduledTime() != null) {
                    // Извлекаем относительное время из scheduledTime (HH:mm)
                    LocalDateTime stepTime = LocalDateTime.ofInstant(step.getScheduledTime(), 
                            java.time.ZoneId.systemDefault());
                    // Берем только часы и минуты как относительное время
                    int hours = stepTime.getHour();
                    int minutes = stepTime.getMinute();
                    // Вычисляем абсолютное время: baseTime + относительное время
                    scheduledTime = baseTime.plusHours(hours).plusMinutes(minutes);
                } else {
                    // Используем delayMs для расчета относительно текущего времени
                    long delaySeconds = step.getDelayMs() / 1000;
                    scheduledTime = baseTime.plusSeconds(delaySeconds);
                }

                // Убеждаемся, что время не в прошлом
                if (scheduledTime.isBefore(LocalDateTime.now())) {
                    scheduledTime = LocalDateTime.now().plusSeconds(1);
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
            logger.error("Ошибка при выполнении сценария", e);
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

