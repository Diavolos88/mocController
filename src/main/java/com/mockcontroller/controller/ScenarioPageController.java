package com.mockcontroller.controller;

import com.mockcontroller.model.Scenario;
import com.mockcontroller.model.Template;
import com.mockcontroller.service.ScenarioService;
import com.mockcontroller.service.ScheduledConfigService;
import com.mockcontroller.service.TemplateService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Controller
public class ScenarioPageController {

    private final ScenarioService scenarioService;
    private final ScheduledConfigService scheduledConfigService;
    private final TemplateService templateService;

    public ScenarioPageController(ScenarioService scenarioService, 
                                  ScheduledConfigService scheduledConfigService,
                                  TemplateService templateService) {
        this.scenarioService = scenarioService;
        this.scheduledConfigService = scheduledConfigService;
        this.templateService = templateService;
    }

    @GetMapping("/scenarios")
    public String scenariosPage(Model model) {
        Collection<Scenario> scenarios = scenarioService.findAll();
        model.addAttribute("scenarios", scenarios);
        return "scenarios";
    }

    @GetMapping("/scenarios/new")
    public String newScenarioPage(@RequestParam(required = false) String system, Model model) {
        // Получаем все системы из шаблонов
        Collection<Template> allTemplates = templateService.findAll();
        Map<String, Map<String, List<Template>>> groupedBySystemAndMock = new HashMap<>();
        Set<String> allSystems = new HashSet<>();
        
        for (Template template : allTemplates) {
            String systemName = template.getSystemName();
            if (isValidTemplate(systemName)) {
                String systemPrefix = extractSystemPrefix(systemName);
                String mockName = systemName;
                
                groupedBySystemAndMock
                    .computeIfAbsent(systemPrefix, k -> new HashMap<>())
                    .computeIfAbsent(mockName, k -> new ArrayList<>())
                    .add(template);
                
                allSystems.add(systemPrefix);
            } else {
                // Для шаблонов, не соответствующих шаблону, используем полное имя как систему
                allSystems.add(systemName);
                groupedBySystemAndMock
                    .computeIfAbsent(systemName, k -> new HashMap<>())
                    .computeIfAbsent(systemName, k -> new ArrayList<>())
                    .add(template);
            }
        }
        
        // Сортируем системы
        List<String> systems = new ArrayList<>(allSystems);
        systems.sort(String::compareToIgnoreCase);
        
        // Если выбрана система, получаем шаблоны для неё
        if (system != null && !system.isEmpty()) {
            Map<String, List<Template>> mocksInSystem = groupedBySystemAndMock.getOrDefault(system, new HashMap<>());
            
            // Если система не найдена в группировке, ищем все шаблоны с таким systemName
            if (mocksInSystem.isEmpty()) {
                for (Template template : allTemplates) {
                    if (template.getSystemName().equals(system) || 
                        (isValidTemplate(template.getSystemName()) && extractSystemPrefix(template.getSystemName()).equals(system))) {
                        mocksInSystem.computeIfAbsent(template.getSystemName(), k -> new ArrayList<>()).add(template);
                    }
                }
            }
            
            List<String> mockNames = new ArrayList<>(mocksInSystem.keySet());
            mockNames.sort(String::compareToIgnoreCase);
            
            // Создаем список заглушек с шаблонами
            List<MockWithTemplates> mocksWithTemplates = new ArrayList<>();
            for (String mockName : mockNames) {
                List<Template> templates = mocksInSystem.get(mockName);
                templates.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                mocksWithTemplates.add(new MockWithTemplates(mockName, templates));
            }
            
            model.addAttribute("selectedSystem", system);
            model.addAttribute("mocksWithTemplates", mocksWithTemplates);
        }
        
        model.addAttribute("systems", systems);
        return "scenario-new";
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

    @GetMapping("/scenarios/{id}")
    public String scenarioPage(@PathVariable String id, Model model) {
        try {
            Scenario scenario = scenarioService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + id));
            model.addAttribute("scenario", scenario);
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
            long cumulativeDelay = 0;

            for (var step : scenario.getSteps()) {
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

                scheduledConfigService.scheduleUpdate(
                        step.getTemplate().getSystemName(),
                        step.getTemplate().getConfig(),
                        scheduledTime,
                        "Сценарий: " + scenario.getName() + " (шаг " + step.getStepOrder() + ")"
                );
            }

            return "redirect:/scenarios/" + id + "?info=" +
                   URLEncoder.encode("Scenario execution started", StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "redirect:/scenarios/" + id + "?error=" +
                   URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
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
}

