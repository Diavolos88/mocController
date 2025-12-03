package com.mockcontroller.controller;

import com.mockcontroller.model.Group;
import com.mockcontroller.model.Scenario;
import com.mockcontroller.model.ScenarioExecutionResponse;
import com.mockcontroller.model.ScenarioStep;
import com.mockcontroller.model.Template;
import com.mockcontroller.service.GroupService;
import com.mockcontroller.service.ScenarioService;
import com.mockcontroller.service.ScheduledConfigService;
import com.mockcontroller.service.TemplateService;
import com.mockcontroller.util.DateTimeUtils;
import com.mockcontroller.util.SystemNameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Controller
public class ScenarioPageController {

    private static final Logger logger = LoggerFactory.getLogger(ScenarioPageController.class);

    private final ScenarioService scenarioService;
    private final ScheduledConfigService scheduledConfigService;
    private final TemplateService templateService;
    private final GroupService groupService;

    public ScenarioPageController(ScenarioService scenarioService,
                                ScheduledConfigService scheduledConfigService,
                                TemplateService templateService,
                                GroupService groupService) {
        this.scenarioService = scenarioService;
        this.scheduledConfigService = scheduledConfigService;
        this.templateService = templateService;
        this.groupService = groupService;
    }

    @GetMapping("/scenarios")
    public String scenariosPage(@RequestParam(required = false) String group, Model model) {
        Collection<Scenario> allScenarios = scenarioService.findAll();
        
        // Группируем сценарии по группам
        Map<String, List<Scenario>> scenariosByGroup = new LinkedHashMap<>();
        Map<String, Group> groupsMap = new HashMap<>();
        
        // Загружаем все группы
        Collection<Group> allGroups = groupService.findAll();
        for (Group g : allGroups) {
            groupsMap.put(g.getId(), g);
        }
        
        // Группируем сценарии и считаем уникальные временные точки для каждого
        Map<String, Integer> scenarioTimePointsCount = new HashMap<>();
        for (Scenario scenario : allScenarios) {
            // Считаем уникальные временные точки для сценария
            Map<String, List<ScenarioStep>> stepsByTime = new LinkedHashMap<>();
            for (ScenarioStep step : scenario.getSteps()) {
                String timeKey;
                if (step.getScheduledTime() != null) {
                    LocalDateTime localDateTime = LocalDateTime.ofInstant(
                        step.getScheduledTime(), 
                        java.time.ZoneId.systemDefault()
                    );
                    timeKey = localDateTime.format(DateTimeUtils.TIME_FORMATTER);
                } else {
                    long delayMs = step.getDelayMs();
                    long hours = delayMs / (1000 * 60 * 60);
                    long minutes = (delayMs % (1000 * 60 * 60)) / (1000 * 60);
                    timeKey = String.format("%02d:%02d", hours, minutes);
                }
                stepsByTime.computeIfAbsent(timeKey, k -> new ArrayList<>()).add(step);
            }
            scenarioTimePointsCount.put(scenario.getId(), stepsByTime.size());
            
            String groupId = scenario.getGroupId();
            if (groupId == null) {
                groupId = "Без группы";
            }
            scenariosByGroup.computeIfAbsent(groupId, k -> new ArrayList<>()).add(scenario);
        }
        
        // Сортируем сценарии внутри каждой группы
        scenariosByGroup.values().forEach(scenarios -> 
            scenarios.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
        );
        
        // Определяем текущую группу
        String currentGroup = group;
        if (currentGroup == null && !scenariosByGroup.isEmpty()) {
            currentGroup = scenariosByGroup.keySet().iterator().next();
        }
        
        // Получаем сценарии для текущей группы
        List<Scenario> currentScenarios = currentGroup != null ? 
            scenariosByGroup.getOrDefault(currentGroup, new ArrayList<>()) : new ArrayList<>();
        
        model.addAttribute("scenariosByGroup", scenariosByGroup);
        model.addAttribute("groupsMap", groupsMap);
        model.addAttribute("currentGroup", currentGroup);
        model.addAttribute("scenarios", currentScenarios);
        model.addAttribute("allGroups", allGroups);
        model.addAttribute("scenarioTimePointsCount", scenarioTimePointsCount);
        return "scenarios";
    }

    @GetMapping("/scenarios/new")
    public String newScenarioPage(Model model) {
        // Получаем все группы
        Collection<Group> groups = groupService.findAll();
        
        model.addAttribute("groups", groups);
        return "scenario-new";
    }

    @GetMapping("/scenarios/{id}")
    public String scenarioPage(@PathVariable String id, Model model) {
        try {
            Scenario scenario = scenarioService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + id));
            
            // Получаем информацию о группе
            Group group = null;
            if (scenario.getGroupId() != null && !scenario.getGroupId().trim().isEmpty()) {
                group = groupService.findById(scenario.getGroupId()).orElse(null);
            }
            
            // Группируем шаги по времени для отображения
            List<ScenarioStep> steps = scenario.getSteps();
            Map<String, List<ScenarioStep>> stepsByTime = new LinkedHashMap<>();
            
            for (ScenarioStep step : steps) {
                String timeKey;
                if (step.getScheduledTime() != null) {
                    // Извлекаем только время (HH:mm) из Instant
                    LocalDateTime localDateTime = LocalDateTime.ofInstant(
                        step.getScheduledTime(), 
                        java.time.ZoneId.systemDefault()
                    );
                    timeKey = localDateTime.format(DateTimeUtils.TIME_FORMATTER);
                } else {
                    // Используем delayMs для вычисления времени
                    long delayMs = step.getDelayMs();
                    long hours = delayMs / (1000 * 60 * 60);
                    long minutes = (delayMs % (1000 * 60 * 60)) / (1000 * 60);
                    timeKey = String.format("%02d:%02d", hours, minutes);
                }
                stepsByTime.computeIfAbsent(timeKey, k -> new ArrayList<>()).add(step);
            }
            
            model.addAttribute("scenario", scenario);
            model.addAttribute("group", group);
            model.addAttribute("stepsByTime", stepsByTime);
            return "scenario-detail";
        } catch (Exception e) {
            model.addAttribute("error", "Error loading scenario: " + e.getMessage());
            return "error";
        }
    }

    @PostMapping("/scenarios/{id}/execute")
    @ResponseBody
    public ResponseEntity<ScenarioExecutionResponse> executeScenario(@PathVariable String id) {
        try {
            Scenario scenario = scenarioService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Scenario not found"));

            if (scenario.getSteps() == null || scenario.getSteps().isEmpty()) {
                logger.warn("Attempted to execute scenario {} with no steps", id);
                return ResponseEntity.badRequest()
                    .body(new ScenarioExecutionResponse(false, "Сценарий не содержит шагов", 0));
            }

            LocalDateTime baseTime = LocalDateTime.now();
            int scheduledCount = 0;

            for (var step : scenario.getSteps()) {
                // Проверяем наличие шаблона
                if (step.getTemplate() == null) {
                    logger.error("Step {} in scenario {} has no template (templateId: {})", 
                        step.getStepOrder(), id, step.getTemplateId());
                    return ResponseEntity.badRequest()
                        .body(new ScenarioExecutionResponse(false, 
                            "Шаг " + step.getStepOrder() + " не содержит шаблона. Возможно, шаблон был удален.", 0));
                }

                if (step.getTemplate().getSystemName() == null || step.getTemplate().getConfig() == null) {
                    logger.error("Step {} in scenario {} has template with null systemName or config", 
                        step.getStepOrder(), id);
                    return ResponseEntity.badRequest()
                        .body(new ScenarioExecutionResponse(false, 
                            "Шаг " + step.getStepOrder() + " содержит некорректный шаблон", 0));
                }

                LocalDateTime scheduledTime;

                if (step.getScheduledTime() != null) {
                    // Если у шага есть scheduledTime, интерпретируем его как относительное смещение от начала дня
                    // Например, 00:01:00 означает "через 1 минуту от текущего момента"
                    LocalDateTime stepTime = LocalDateTime.ofInstant(step.getScheduledTime(), java.time.ZoneId.systemDefault());
                    int hours = stepTime.getHour();
                    int minutes = stepTime.getMinute();
                    int seconds = stepTime.getSecond();
                    
                    // Вычисляем смещение в секундах от начала дня (00:00:00)
                    long offsetSeconds = hours * 3600L + minutes * 60L + seconds;
                    
                    // Применяем это смещение к baseTime
                    scheduledTime = baseTime.plusSeconds(offsetSeconds);
                } else {
                    // Если нет scheduledTime, используем delayMs относительно baseTime
                    long delaySeconds = step.getDelayMs() / 1000;
                    scheduledTime = baseTime.plusSeconds(delaySeconds);
                }

                // Убеждаемся, что время не в прошлом
                LocalDateTime now = LocalDateTime.now();
                if (scheduledTime.isBefore(now)) {
                    scheduledTime = now.plusSeconds(1);
                }

                // Используем комментарий из шага, если он есть, иначе создаем стандартный
                String comment = step.getComment();
                if (comment == null || comment.trim().isEmpty()) {
                    comment = "Сценарий: " + scenario.getName() + " (шаг " + step.getStepOrder() + ")";
                }
                
                try {
                    scheduledConfigService.scheduleUpdate(
                            step.getTemplate().getSystemName(),
                            step.getTemplate().getConfig(),
                            scheduledTime,
                            comment
                    );
                    scheduledCount++;
                    logger.info("Scheduled step {} of scenario {} for system {} at {}", 
                        step.getStepOrder(), id, step.getTemplate().getSystemName(), scheduledTime);
                } catch (Exception e) {
                    logger.error("Failed to schedule step {} of scenario {}: {}", 
                        step.getStepOrder(), id, e.getMessage(), e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ScenarioExecutionResponse(false, 
                            "Ошибка при планировании шага " + step.getStepOrder() + ": " + e.getMessage(), scheduledCount));
                }
            }

            logger.info("Successfully scheduled {} steps for scenario {}", scheduledCount, id);
            String message = "Сценарий успешно запланирован (" + scheduledCount + " шагов)";
            return ResponseEntity.ok(new ScenarioExecutionResponse(true, message, scheduledCount));
        } catch (IllegalArgumentException e) {
            logger.error("Scenario execution failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(new ScenarioExecutionResponse(false, e.getMessage(), 0));
        } catch (Exception e) {
            logger.error("Unexpected error executing scenario {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ScenarioExecutionResponse(false, "Ошибка при выполнении сценария: " + e.getMessage(), 0));
        }
    }

    @PostMapping("/scenarios/{id}/execute-scheduled")
    @ResponseBody
    public ResponseEntity<ScenarioExecutionResponse> executeScenarioScheduled(@PathVariable String id,
                                          @RequestParam String scheduledDateTime,
                                          @RequestParam(required = false) String scheduleComment) {
        try {
            Scenario scenario = scenarioService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Scenario not found"));

            // Парсим время начала сценария
            LocalDateTime startTime;
            try {
                java.time.format.DateTimeFormatter formatter = DateTimeUtils.DATE_TIME_FORMATTER;
                startTime = LocalDateTime.parse(scheduledDateTime, formatter);
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                    .body(new ScenarioExecutionResponse(false, 
                        "Неверный формат даты и времени. Используйте: HH:mm:ss dd-MM-yyyy", 0));
            }

            // Проверяем, что время начала не в прошлом
            if (startTime.isBefore(LocalDateTime.now())) {
                return ResponseEntity.badRequest()
                    .body(new ScenarioExecutionResponse(false, 
                        "Время начала сценария не может быть в прошлом", 0));
            }

            if (scenario.getSteps() == null || scenario.getSteps().isEmpty()) {
                logger.warn("Attempted to execute scheduled scenario {} with no steps", id);
                return ResponseEntity.badRequest()
                    .body(new ScenarioExecutionResponse(false, "Сценарий не содержит шагов", 0));
            }

            // Применяем шаги сценария относительно времени начала
            int scheduledCount = 0;
            for (var step : scenario.getSteps()) {
                // Проверяем наличие шаблона
                if (step.getTemplate() == null) {
                    logger.error("Step {} in scenario {} has no template (templateId: {})", 
                        step.getStepOrder(), id, step.getTemplateId());
                    return ResponseEntity.badRequest()
                        .body(new ScenarioExecutionResponse(false, 
                            "Шаг " + step.getStepOrder() + " не содержит шаблона. Возможно, шаблон был удален.", scheduledCount));
                }

                if (step.getTemplate().getSystemName() == null || step.getTemplate().getConfig() == null) {
                    logger.error("Step {} in scenario {} has template with null systemName or config", 
                        step.getStepOrder(), id);
                    return ResponseEntity.badRequest()
                        .body(new ScenarioExecutionResponse(false, 
                            "Шаг " + step.getStepOrder() + " содержит некорректный шаблон", scheduledCount));
                }

                LocalDateTime scheduledTime;

                if (step.getScheduledTime() != null) {
                    // Если у шага есть scheduledTime, интерпретируем его как относительное смещение от начала дня
                    // Например, 00:01:00 означает "через 1 минуту от времени начала сценария"
                    LocalDateTime stepTime = LocalDateTime.ofInstant(step.getScheduledTime(), java.time.ZoneId.systemDefault());
                    int hours = stepTime.getHour();
                    int minutes = stepTime.getMinute();
                    int seconds = stepTime.getSecond();
                    
                    // Вычисляем смещение в секундах от начала дня (00:00:00)
                    long offsetSeconds = hours * 3600L + minutes * 60L + seconds;
                    
                    // Применяем это смещение к startTime
                    scheduledTime = startTime.plusSeconds(offsetSeconds);
                } else {
                    // Если нет scheduledTime, используем delayMs относительно startTime
                    long delaySeconds = step.getDelayMs() / 1000;
                    scheduledTime = startTime.plusSeconds(delaySeconds);
                }

                // Убеждаемся, что время не в прошлом
                if (scheduledTime.isBefore(LocalDateTime.now())) {
                    scheduledTime = LocalDateTime.now().plusSeconds(1);
                }

                String comment = scheduleComment != null && !scheduleComment.trim().isEmpty() ?
                        scheduleComment : "Сценарий: " + scenario.getName() + " (шаг " + step.getStepOrder() + ")";

                try {
                    scheduledConfigService.scheduleUpdate(
                            step.getTemplate().getSystemName(),
                            step.getTemplate().getConfig(),
                            scheduledTime,
                            comment
                    );
                    scheduledCount++;
                    logger.info("Scheduled step {} of scenario {} for system {} at {}", 
                        step.getStepOrder(), id, step.getTemplate().getSystemName(), scheduledTime);
                } catch (Exception e) {
                    logger.error("Failed to schedule step {} of scenario {}: {}", 
                        step.getStepOrder(), id, e.getMessage(), e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ScenarioExecutionResponse(false, 
                            "Ошибка при планировании шага " + step.getStepOrder() + ": " + e.getMessage(), scheduledCount));
                }
            }

            logger.info("Successfully scheduled {} steps for scenario {} starting at {}", scheduledCount, id, scheduledDateTime);

            String message = "Сценарий успешно запланирован на " + scheduledDateTime + " (" + scheduledCount + " шагов)";
            return ResponseEntity.ok(new ScenarioExecutionResponse(true, message, scheduledCount));
        } catch (IllegalArgumentException e) {
            logger.error("Scenario scheduling failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(new ScenarioExecutionResponse(false, "Сценарий не найден", 0));
        } catch (Exception e) {
            logger.error("Unexpected error scheduling scenario {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ScenarioExecutionResponse(false, "Ошибка при планировании сценария: " + e.getMessage(), 0));
        }
    }

    @PostMapping("/scenarios/{id}/delete")
    public String deleteScenario(@PathVariable String id) {
        try {
            scenarioService.deleteScenario(id);
            return "redirect:/scenarios?info=" +
                   URLEncoder.encode("Сценарий успешно удален", StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "redirect:/scenarios?error=" +
                   URLEncoder.encode("Сценарий не найден", StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "redirect:/scenarios?error=" +
                   URLEncoder.encode("Ошибка при удалении сценария: " + e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @GetMapping("/scenarios/{id}/edit")
    public String editScenarioPage(@PathVariable String id, Model model) {
        try {
            Scenario scenario = scenarioService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + id));
            
            // Получаем все группы
            Collection<Group> groups = groupService.findAll();
            
            // Если у сценария нет группы, это ошибка
            if (scenario.getGroupId() == null || scenario.getGroupId().trim().isEmpty()) {
                throw new IllegalArgumentException("У сценария не указана группа");
            }
            
            // Получаем шаблоны для группы сценария
            List<String> groupSystems = groupService.getSystemsByGroupId(scenario.getGroupId());
            Collection<Template> allTemplates = templateService.findAll();
            Map<String, List<Template>> templatesByMock = new LinkedHashMap<>();
            
            // Извлекаем уникальные префиксы систем из группы (для шаблонов system-integration-mock)
            Set<String> groupSystemPrefixes = new HashSet<>();
            for (String groupSystem : groupSystems) {
                if (SystemNameUtils.isValidTemplate(groupSystem)) {
                    String systemPrefix = SystemNameUtils.extractSystemPrefix(groupSystem);
                    groupSystemPrefixes.add(systemPrefix);
                } else {
                    // Если не соответствует шаблону, используем полное имя
                    groupSystemPrefixes.add(groupSystem);
                }
            }
            
            for (Template template : allTemplates) {
                String systemName = template.getSystemName();
                // Проверяем, принадлежит ли шаблон системе из группы
                boolean belongsToGroup = false;
                String templatePrefix = null;
                
                if (SystemNameUtils.isValidTemplate(systemName)) {
                    templatePrefix = SystemNameUtils.extractSystemPrefix(systemName);
                    // Сравниваем префикс шаблона с префиксами систем из группы
                    if (groupSystemPrefixes.contains(templatePrefix)) {
                        belongsToGroup = true;
                    }
                } else {
                    // Если шаблон не соответствует формату, сравниваем полное имя
                    if (groupSystemPrefixes.contains(systemName)) {
                        belongsToGroup = true;
                    }
                }
                
                if (belongsToGroup) {
                    String mockName = systemName;
                    templatesByMock.computeIfAbsent(mockName, k -> new ArrayList<>()).add(template);
                }
            }
            
            // Сортируем шаблоны внутри каждой группы mock
            templatesByMock.values().forEach(templates -> 
                templates.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
            );
            
            // Создаем список заглушек с шаблонами
            List<String> mockNames = new ArrayList<>(templatesByMock.keySet());
            mockNames.sort(String::compareToIgnoreCase);
            List<MockWithTemplates> mocksWithTemplates = new ArrayList<>();
            for (String mockName : mockNames) {
                mocksWithTemplates.add(new MockWithTemplates(mockName, templatesByMock.get(mockName)));
            }
            
            // Получаем ID шаблонов, используемых в сценарии
            Set<String> usedTemplateIds = new HashSet<>();
            for (var step : scenario.getSteps()) {
                if (step.getTemplateId() != null) {
                    usedTemplateIds.add(step.getTemplateId());
                }
            }
            
            model.addAttribute("groups", groups);
            model.addAttribute("scenario", scenario);
            model.addAttribute("mocksWithTemplates", mocksWithTemplates);
            model.addAttribute("usedTemplateIds", new ArrayList<>(usedTemplateIds));
            return "scenario-edit";
        } catch (Exception e) {
            model.addAttribute("error", "Error loading scenario: " + e.getMessage());
            return "error";
        }
    }


    public static class MockWithTemplates {
        private final String mockName;
        private final List<Template> templates;

        public MockWithTemplates(String mockName, List<Template> templates) {
            this.mockName = mockName;
            this.templates = templates;
        }

        public String getMockName() {
            return mockName;
        }

        public List<Template> getTemplates() {
            return templates;
        }
    }
}
