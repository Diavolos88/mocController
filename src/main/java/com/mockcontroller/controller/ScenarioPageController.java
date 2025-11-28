package com.mockcontroller.controller;

import com.mockcontroller.model.Group;
import com.mockcontroller.model.Scenario;
import com.mockcontroller.model.ScenarioStep;
import com.mockcontroller.model.Template;
import com.mockcontroller.service.GroupService;
import com.mockcontroller.service.ScenarioService;
import com.mockcontroller.service.ScheduledConfigService;
import com.mockcontroller.service.TemplateService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
public class ScenarioPageController {

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
        
        // Группируем сценарии
        for (Scenario scenario : allScenarios) {
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
                    timeKey = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
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
    public String executeScenario(@PathVariable String id) {
        try {
            Scenario scenario = scenarioService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Scenario not found"));

            LocalDateTime baseTime = LocalDateTime.now();

            for (var step : scenario.getSteps()) {
                LocalDateTime scheduledTime;

                if (step.getScheduledTime() != null) {
                    // Извлекаем относительное время из scheduledTime (HH:mm)
                    // scheduledTime хранится как Instant, но содержит только время (часы и минуты)
                    LocalDateTime stepTime = LocalDateTime.ofInstant(step.getScheduledTime(), java.time.ZoneId.systemDefault());
                    int hours = stepTime.getHour();
                    int minutes = stepTime.getMinute();
                    int seconds = stepTime.getSecond();
                    
                    // Вычисляем абсолютное время относительно текущего момента
                    scheduledTime = baseTime.plusHours(hours).plusMinutes(minutes).plusSeconds(seconds);
                } else {
                    long delaySeconds = step.getDelayMs() / 1000;
                    scheduledTime = baseTime.plusSeconds(delaySeconds);
                }

                // Убеждаемся, что время не в прошлом
                if (scheduledTime.isBefore(LocalDateTime.now())) {
                    scheduledTime = LocalDateTime.now().plusSeconds(1);
                }

                scheduledConfigService.scheduleUpdate(
                        step.getTemplate().getSystemName(),
                        step.getTemplate().getConfig(),
                        scheduledTime,
                        "Сценарий: " + scenario.getName() + " (шаг " + step.getStepOrder() + ")"
                );
            }

            return "redirect:/scenarios/" + id + "?info=" +
                   URLEncoder.encode("Сценарий запущен", StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "redirect:/scenarios/" + id + "?error=" +
                   URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @PostMapping("/scenarios/{id}/execute-scheduled")
    public String executeScenarioScheduled(@PathVariable String id,
                                          @RequestParam String scheduledDateTime,
                                          @RequestParam(required = false) String scheduleComment) {
        try {
            Scenario scenario = scenarioService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Scenario not found"));

            // Парсим время начала сценария
            LocalDateTime startTime;
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy");
                startTime = LocalDateTime.parse(scheduledDateTime, formatter);
            } catch (Exception e) {
                return "redirect:/scenarios?error=" +
                       URLEncoder.encode("Неверный формат даты и времени. Используйте: HH:mm:ss dd-MM-yyyy", StandardCharsets.UTF_8);
            }

            // Проверяем, что время начала не в прошлом
            if (startTime.isBefore(LocalDateTime.now())) {
                return "redirect:/scenarios?error=" +
                       URLEncoder.encode("Время начала сценария не может быть в прошлом", StandardCharsets.UTF_8);
            }

            // Применяем шаги сценария относительно времени начала
            for (var step : scenario.getSteps()) {
                LocalDateTime scheduledTime;

                if (step.getScheduledTime() != null) {
                    // Если у шага есть scheduledTime, используем его как абсолютное время
                    scheduledTime = LocalDateTime.ofInstant(step.getScheduledTime(), java.time.ZoneId.systemDefault());
                    // Но пересчитываем относительно startTime
                    long stepDelaySeconds = step.getDelayMs() / 1000;
                    scheduledTime = startTime.plusSeconds(stepDelaySeconds);
                } else {
                    // Используем delayMs относительно startTime
                    long delaySeconds = step.getDelayMs() / 1000;
                    scheduledTime = startTime.plusSeconds(delaySeconds);
                }

                // Убеждаемся, что время не в прошлом
                if (scheduledTime.isBefore(LocalDateTime.now())) {
                    scheduledTime = LocalDateTime.now().plusSeconds(1);
                }

                String comment = scheduleComment != null && !scheduleComment.trim().isEmpty() ?
                        scheduleComment : "Сценарий: " + scenario.getName() + " (шаг " + step.getStepOrder() + ")";

                scheduledConfigService.scheduleUpdate(
                        step.getTemplate().getSystemName(),
                        step.getTemplate().getConfig(),
                        scheduledTime,
                        comment
                );
            }

            return "redirect:/scenarios/" + id + "?info=" +
                   URLEncoder.encode("Сценарий запланирован на " + scheduledDateTime, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "redirect:/scenarios?error=" +
                   URLEncoder.encode("Сценарий не найден", StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "redirect:/scenarios?error=" +
                   URLEncoder.encode("Ошибка при планировании сценария: " + e.getMessage(), StandardCharsets.UTF_8);
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
                if (isValidTemplate(groupSystem)) {
                    String systemPrefix = extractSystemPrefix(groupSystem);
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
                
                if (isValidTemplate(systemName)) {
                    templatePrefix = extractSystemPrefix(systemName);
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

    private boolean isValidTemplate(String systemName) {
        if (systemName == null || systemName.isEmpty()) {
            return false;
        }
        int dashCount = 0;
        for (char c : systemName.toCharArray()) {
            if (c == '-') {
                dashCount++;
            }
        }
        return dashCount >= 2 && systemName.endsWith("-mock");
    }
    
    private String extractSystemPrefix(String systemName) {
        int firstDashIndex = systemName.indexOf('-');
        if (firstDashIndex > 0) {
            return systemName.substring(0, firstDashIndex);
        }
        return systemName;
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
