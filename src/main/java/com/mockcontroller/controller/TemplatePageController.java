package com.mockcontroller.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.mockcontroller.model.Template;
import com.mockcontroller.service.ConfigService;
import com.mockcontroller.service.TemplateService;
import com.mockcontroller.util.SystemNameUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
public class TemplatePageController {

    private final TemplateService templateService;
    private final ConfigService configService;

    public TemplatePageController(TemplateService templateService, ConfigService configService) {
        this.templateService = templateService;
        this.configService = configService;
    }

    @GetMapping("/templates")
    public String templatesPage(@RequestParam(required = false) String system, 
                                @RequestParam(required = false) String mock, 
                                Model model) {
        Collection<Template> allTemplates = templateService.findAll();
        
        // Разделяем на правильные (по шаблону) и неправильные
        Map<String, Map<String, List<Template>>> groupedBySystemAndMock = new HashMap<>();
        List<Template> invalidTemplates = new ArrayList<>();
        
        for (Template template : allTemplates) {
            String systemName = template.getSystemName();
            // Проверяем шаблон: название_системы-название_эмуляции-mock
            if (SystemNameUtils.isValidTemplate(systemName)) {
                String systemPrefix = SystemNameUtils.extractSystemPrefix(systemName);
                String mockName = systemName; // Полное имя заглушки для второго уровня
                
                groupedBySystemAndMock
                    .computeIfAbsent(systemPrefix, k -> new HashMap<>())
                    .computeIfAbsent(mockName, k -> new ArrayList<>())
                    .add(template);
            } else {
                invalidTemplates.add(template);
            }
        }
        
        // Сортируем неправильные шаблоны по имени
        invalidTemplates.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        
        // Сортируем шаблоны внутри каждой заглушки по имени
        for (Map<String, List<Template>> mocks : groupedBySystemAndMock.values()) {
            for (List<Template> templates : mocks.values()) {
                templates.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            }
        }
        
        // Сортируем системы по алфавиту
        List<String> systems = new ArrayList<>(groupedBySystemAndMock.keySet());
        systems.sort(String::compareToIgnoreCase);
        
        // Создаем список объектов с системой и количеством для удобного отображения
        List<SystemInfo> systemsWithCounts = new ArrayList<>();
        for (String sys : systems) {
            int totalCount = groupedBySystemAndMock.get(sys).values().stream()
                .mapToInt(List::size)
                .sum();
            systemsWithCounts.add(new SystemInfo(sys, totalCount, false));
        }
        
        // Добавляем "Шаблоны" (неправильные) в список, если они есть
        if (!invalidTemplates.isEmpty()) {
            systemsWithCounts.add(new SystemInfo("Шаблоны", invalidTemplates.size(), true));
        }
        
        // Если указана система, фильтруем
        if (system != null && !system.isEmpty()) {
            if ("Шаблоны".equals(system)) {
                // Показываем неправильные шаблоны
                model.addAttribute("templates", invalidTemplates);
                model.addAttribute("currentSystem", "Шаблоны");
                model.addAttribute("currentMock", null);
                model.addAttribute("mocksWithCounts", new ArrayList<>());
            } else {
                Map<String, List<Template>> mocksInSystem = groupedBySystemAndMock.getOrDefault(system, new HashMap<>());
                
                // Создаем список заглушек в системе с количеством шаблонов
                List<MockInfo> mocksWithCounts = new ArrayList<>();
                List<String> mockNames = new ArrayList<>(mocksInSystem.keySet());
                mockNames.sort(String::compareToIgnoreCase);
                for (String mockName : mockNames) {
                    mocksWithCounts.add(new MockInfo(mockName, mocksInSystem.get(mockName).size()));
                }
                model.addAttribute("mocksWithCounts", mocksWithCounts);
                
                // Если указана конкретная заглушка, фильтруем по ней
                if (mock != null && !mock.isEmpty()) {
                    List<Template> filteredTemplates = mocksInSystem.getOrDefault(mock, new ArrayList<>());
                    model.addAttribute("templates", filteredTemplates);
                    model.addAttribute("currentSystem", system);
                    model.addAttribute("currentMock", mock);
                } else {
                    // Показываем первую заглушку по умолчанию
                    if (!mockNames.isEmpty()) {
                        String firstMock = mockNames.get(0);
                        model.addAttribute("templates", mocksInSystem.get(firstMock));
                        model.addAttribute("currentSystem", system);
                        model.addAttribute("currentMock", firstMock);
                    } else {
                        model.addAttribute("templates", new ArrayList<>());
                        model.addAttribute("currentSystem", system);
                        model.addAttribute("currentMock", null);
                    }
                }
            }
        } else {
            // Показываем первую систему по умолчанию
            if (!systems.isEmpty()) {
                String firstSystem = systems.get(0);
                Map<String, List<Template>> mocksInSystem = groupedBySystemAndMock.get(firstSystem);
                List<String> mockNames = new ArrayList<>(mocksInSystem.keySet());
                mockNames.sort(String::compareToIgnoreCase);
                
                // Создаем список заглушек
                List<MockInfo> mocksWithCounts = new ArrayList<>();
                for (String mockName : mockNames) {
                    mocksWithCounts.add(new MockInfo(mockName, mocksInSystem.get(mockName).size()));
                }
                model.addAttribute("mocksWithCounts", mocksWithCounts);
                
                if (!mockNames.isEmpty()) {
                    String firstMock = mockNames.get(0);
                    model.addAttribute("templates", mocksInSystem.get(firstMock));
                    model.addAttribute("currentSystem", firstSystem);
                    model.addAttribute("currentMock", firstMock);
                } else {
                    model.addAttribute("templates", new ArrayList<>());
                    model.addAttribute("currentSystem", firstSystem);
                    model.addAttribute("currentMock", null);
                }
            } else if (!invalidTemplates.isEmpty()) {
                // Если нет правильных систем, но есть неправильные - показываем их
                model.addAttribute("templates", invalidTemplates);
                model.addAttribute("currentSystem", "Шаблоны");
                model.addAttribute("currentMock", null);
                model.addAttribute("mocksWithCounts", new ArrayList<>());
            } else {
                model.addAttribute("templates", new ArrayList<>());
                model.addAttribute("currentSystem", null);
                model.addAttribute("currentMock", null);
                model.addAttribute("mocksWithCounts", new ArrayList<>());
            }
        }
        
        model.addAttribute("systemsWithCounts", systemsWithCounts);
        return "templates";
    }
    
    
    public static class SystemInfo {
        private final String name;
        private final int count;
        private final boolean invalid;
        
        public SystemInfo(String name, int count, boolean invalid) {
            this.name = name;
            this.count = count;
            this.invalid = invalid;
        }
        
        public String getName() {
            return name;
        }
        
        public int getCount() {
            return count;
        }
        
        public boolean isInvalid() {
            return invalid;
        }
    }
    
    public static class MockInfo {
        private final String name;
        private final int count;
        
        public MockInfo(String name, int count) {
            this.name = name;
            this.count = count;
        }
        
        public String getName() {
            return name;
        }
        
        public int getCount() {
            return count;
        }
    }

    @GetMapping("/templates/{id}")
    public String templatePage(@PathVariable String id, Model model) {
        try {
            Template template = templateService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
            model.addAttribute("template", template);
            
            // Преобразуем JsonNode в Map для удобного отображения в Thymeleaf
            if (template.getConfig() != null) {
                JsonNode config = template.getConfig();
                
                // Delays
                Map<String, String> delays = new LinkedHashMap<>();
                if (config.has("delays") && config.get("delays").isObject()) {
                    config.get("delays").fields().forEachRemaining(entry -> {
                        delays.put(entry.getKey(), entry.getValue().asText());
                    });
                }
                model.addAttribute("delays", delays);
                
                // String Parameters
                Map<String, String> stringParams = new LinkedHashMap<>();
                if (config.has("stringParams") && config.get("stringParams").isObject()) {
                    config.get("stringParams").fields().forEachRemaining(entry -> {
                        stringParams.put(entry.getKey(), entry.getValue().asText());
                    });
                }
                model.addAttribute("stringParams", stringParams);
                
                // Logging Level
                String loggingLv = null;
                if (config.has("loggingLv")) {
                    loggingLv = config.get("loggingLv").asText();
                }
                model.addAttribute("loggingLv", loggingLv);
            }
            
            return "template-detail";
        } catch (Exception e) {
            model.addAttribute("error", "Error loading template: " + e.getMessage());
            return "error";
        }
    }

    @PostMapping("/templates/{id}/apply")
    public String applyTemplate(@PathVariable String id, @RequestParam String systemName) {
        try {
            Template template = templateService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Template not found"));

            if (!template.getSystemName().equals(systemName)) {
                return "redirect:/templates/" + id + "?error=" +
                       java.net.URLEncoder.encode("Template is for different system", StandardCharsets.UTF_8);
            }

            configService.updateCurrentConfig(systemName, template.getConfig());
            return "redirect:/configs/" + java.net.URLEncoder.encode(systemName, StandardCharsets.UTF_8) +
                   "?info=" + java.net.URLEncoder.encode("Template applied successfully", StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "redirect:/templates/" + id + "?error=" +
                   java.net.URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @GetMapping("/templates/{id}/edit")
    public String editTemplatePage(@PathVariable String id, Model model) {
        try {
            Template template = templateService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
            model.addAttribute("template", template);
            
            // Преобразуем JsonNode в Map для удобного отображения в Thymeleaf
            if (template.getConfig() != null) {
                JsonNode config = template.getConfig();
                
                // Delays
                Map<String, String> delays = new LinkedHashMap<>();
                if (config.has("delays") && config.get("delays").isObject()) {
                    config.get("delays").fields().forEachRemaining(entry -> {
                        delays.put(entry.getKey(), entry.getValue().asText());
                    });
                }
                model.addAttribute("delays", delays);
                
                // String Parameters
                Map<String, String> stringParams = new LinkedHashMap<>();
                if (config.has("stringParams") && config.get("stringParams").isObject()) {
                    config.get("stringParams").fields().forEachRemaining(entry -> {
                        stringParams.put(entry.getKey(), entry.getValue().asText());
                    });
                }
                model.addAttribute("stringParams", stringParams);
                
                // Logging Level
                String loggingLv = null;
                if (config.has("loggingLv")) {
                    loggingLv = config.get("loggingLv").asText();
                }
                model.addAttribute("loggingLv", loggingLv);
            }
            
            return "template-edit";
        } catch (Exception e) {
            model.addAttribute("error", "Error loading template: " + e.getMessage());
            return "error";
        }
    }

    @PostMapping("/templates/{id}/edit")
    public String updateTemplate(@PathVariable String id,
                                 @RequestParam String name,
                                 @RequestParam(required = false) String description,
                                 @RequestParam Map<String, String> allParams) {
        try {
            templateService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));

            // Собираем delays
            Map<String, String> delays = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("delay_")) {
                    String key = entry.getKey().substring(6);
                    if (entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                        delays.put(key, entry.getValue());
                    }
                }
            }

            // Собираем stringParams
            Map<String, String> stringParams = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("string_")) {
                    String key = entry.getKey().substring(7);
                    if (entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                        stringParams.put(key, entry.getValue());
                    }
                }
            }

            // Получаем loggingLv
            String loggingLv = allParams.get("loggingLv");

            // Создаем новый конфиг
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode newConfig = objectMapper.createObjectNode();
            
            com.fasterxml.jackson.databind.node.ObjectNode delaysNode = objectMapper.createObjectNode();
            delays.forEach((key, value) -> {
                try {
                    delaysNode.put(key, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    delaysNode.put(key, value);
                }
            });
            newConfig.set("delays", delaysNode);
            
            com.fasterxml.jackson.databind.node.ObjectNode stringParamsNode = objectMapper.createObjectNode();
            stringParams.forEach((key, value) -> {
                if (value != null) {
                    stringParamsNode.put(key, value);
                }
            });
            newConfig.set("stringParams", stringParamsNode);
            
            if (loggingLv != null && !loggingLv.trim().isEmpty()) {
                newConfig.put("loggingLv", loggingLv);
            }

            // Обновляем шаблон
            templateService.updateTemplate(id, name, newConfig, description);

            return "redirect:/templates/" + id + "?info=" +
                   java.net.URLEncoder.encode("Шаблон успешно обновлен", StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "redirect:/templates/" + id + "?error=" +
                   java.net.URLEncoder.encode("Шаблон не найден", StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "redirect:/templates/" + id + "/edit?error=" +
                   java.net.URLEncoder.encode("Ошибка при обновлении шаблона: " + e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @PostMapping("/templates/{id}/delete")
    public String deleteTemplate(@PathVariable String id) {
        try {
            templateService.deleteTemplate(id);
            return "redirect:/templates?info=" +
                   java.net.URLEncoder.encode("Шаблон успешно удален", StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "redirect:/templates?error=" +
                   java.net.URLEncoder.encode("Шаблон не найден", StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "redirect:/templates?error=" +
                   java.net.URLEncoder.encode("Ошибка при удалении шаблона: " + e.getMessage(), StandardCharsets.UTF_8);
        }
    }
}

