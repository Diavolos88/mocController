package com.mockcontroller.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.mockcontroller.dto.ConfigViewDto;
import com.mockcontroller.model.StoredConfig;
import com.mockcontroller.service.ConfigService;
import com.mockcontroller.service.ScheduledConfigService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.mockcontroller.model.ScheduledConfigUpdate;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ConfigPageController {

    private final ConfigService configService;
    private final ScheduledConfigService scheduledConfigService;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy");
    
    // Внутренний класс для передачи данных системы
    public static class SystemInfo {
        private final String name;
        private final int count;
        private final boolean isInvalid;
        
        public SystemInfo(String name, int count, boolean isInvalid) {
            this.name = name;
            this.count = count;
            this.isInvalid = isInvalid;
        }
        
        public String getName() { return name; }
        public int getCount() { return count; }
        public boolean isInvalid() { return isInvalid; }
    }

    public ConfigPageController(ConfigService configService, ScheduledConfigService scheduledConfigService) {
        this.configService = configService;
        this.scheduledConfigService = scheduledConfigService;
    }

    @GetMapping("/")
    public String landing(@RequestParam(required = false) String system, Model model) {
        List<StoredConfig> allConfigs = new ArrayList<>(configService.findAll());
        
        // Разделяем на правильные (по шаблону) и неправильные
        Map<String, List<StoredConfig>> groupedBySystem = new HashMap<>();
        List<StoredConfig> invalidConfigs = new ArrayList<>();
        
        for (StoredConfig config : allConfigs) {
            String systemName = config.getSystemName();
            // Проверяем шаблон: название_системы-название_эмуляции-mock
            if (isValidTemplate(systemName)) {
                String systemPrefix = extractSystemPrefix(systemName);
                groupedBySystem.computeIfAbsent(systemPrefix, k -> new ArrayList<>()).add(config);
            } else {
                invalidConfigs.add(config);
            }
        }
        
        // Сортируем неправильные заглушки по алфавиту
        invalidConfigs.sort((a, b) -> a.getSystemName().compareToIgnoreCase(b.getSystemName()));
        
        // Сортируем заглушки внутри каждой системы по алфавиту
        for (List<StoredConfig> configs : groupedBySystem.values()) {
            configs.sort((a, b) -> a.getSystemName().compareToIgnoreCase(b.getSystemName()));
        }
        
        // Сортируем системы по алфавиту
        List<String> systems = new ArrayList<>(groupedBySystem.keySet());
        systems.sort(String::compareToIgnoreCase);
        
        // Создаем список объектов с системой и количеством для удобного отображения
        List<SystemInfo> systemsWithCounts = new ArrayList<>();
        for (String sys : systems) {
            systemsWithCounts.add(new SystemInfo(sys, groupedBySystem.get(sys).size(), false));
        }
        
        // Добавляем "Заглушки" (неправильные) в список, если они есть
        if (!invalidConfigs.isEmpty()) {
            systemsWithCounts.add(new SystemInfo("Заглушки", invalidConfigs.size(), true));
        }
        
        // Если указана система, фильтруем
        if (system != null && !system.isEmpty()) {
            if ("Заглушки".equals(system)) {
                // Показываем неправильные заглушки
                model.addAttribute("configs", invalidConfigs);
                model.addAttribute("currentSystem", "Заглушки");
            } else {
                List<StoredConfig> filteredConfigs = groupedBySystem.getOrDefault(system, new ArrayList<>());
                model.addAttribute("configs", filteredConfigs);
                model.addAttribute("currentSystem", system);
            }
        } else {
            // Показываем первую систему по умолчанию
            if (!systems.isEmpty()) {
                String firstSystem = systems.get(0);
                model.addAttribute("configs", groupedBySystem.get(firstSystem));
                model.addAttribute("currentSystem", firstSystem);
            } else if (!invalidConfigs.isEmpty()) {
                // Если нет правильных систем, но есть неправильные - показываем их
                model.addAttribute("configs", invalidConfigs);
                model.addAttribute("currentSystem", "Заглушки");
            } else {
                model.addAttribute("configs", new ArrayList<>());
                model.addAttribute("currentSystem", null);
            }
        }
        
        model.addAttribute("systemsWithCounts", systemsWithCounts);
        
        // Форматируем даты только для отображения (хранение не меняется)
        Map<String, String> formattedDates = new HashMap<>();
        for (StoredConfig config : allConfigs) {
            if (config.getUpdatedAt() != null) {
                formattedDates.put(config.getSystemName(), 
                    config.getUpdatedAt().atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
        }
        model.addAttribute("formattedDates", formattedDates);
        
        return "index";
    }
    
    @GetMapping("/invalid")
    public String invalidConfigs(Model model) {
        List<StoredConfig> allConfigs = new ArrayList<>(configService.findAll());
        List<StoredConfig> invalidConfigs = allConfigs.stream()
                .filter(config -> !isValidTemplate(config.getSystemName()))
                .sorted((a, b) -> a.getSystemName().compareToIgnoreCase(b.getSystemName()))
                .toList();
        
        // Форматируем даты только для отображения (хранение не меняется)
        Map<String, String> formattedDates = new HashMap<>();
        for (StoredConfig config : invalidConfigs) {
            if (config.getUpdatedAt() != null) {
                formattedDates.put(config.getSystemName(), 
                    config.getUpdatedAt().atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
        }
        
        model.addAttribute("configs", invalidConfigs);
        model.addAttribute("formattedDates", formattedDates);
        return "invalid";
    }
    
    private boolean isValidTemplate(String systemName) {
        if (systemName == null || systemName.isEmpty()) {
            return false;
        }
        // Шаблон: название_системы-название_эмуляции-mock
        // Должно быть минимум 2 тире и заканчиваться на -mock
        int dashCount = 0;
        for (char c : systemName.toCharArray()) {
            if (c == '-') {
                dashCount++;
            }
        }
        return dashCount >= 2 && systemName.endsWith("-mock");
    }
    
    private String extractSystemPrefix(String systemName) {
        // Берем первое слово до первого тире
        int firstDashIndex = systemName.indexOf('-');
        if (firstDashIndex > 0) {
            return systemName.substring(0, firstDashIndex);
        }
        return systemName;
    }

    @GetMapping("/configs/{systemName}")
    public String configPage(@PathVariable String systemName, Model model) {
        try {
            // Р”РµРєРѕРґРёСЂСѓРµРј URL-РєРѕРґРёСЂРѕРІР°РЅРёРµ
            String decodedName = URLDecoder.decode(systemName, StandardCharsets.UTF_8);
            StoredConfig config = configService.findBySystemName(decodedName)
                    .orElseThrow(() -> new IllegalArgumentException("Config not found: " + decodedName));
            
            ConfigViewDto configView = configService.toConfigViewDto(config);
            model.addAttribute("configView", configView);
            model.addAttribute("systemName", decodedName);
            
            // Получаем все запланированные обновления и форматируем даты
            List<ScheduledConfigUpdate> scheduledUpdates = scheduledConfigService.getScheduledUpdates(decodedName);
            Map<String, String> formattedScheduledTimes = new HashMap<>();
            for (ScheduledConfigUpdate update : scheduledUpdates) {
                formattedScheduledTimes.put(update.getId(), update.getScheduledTime().format(DATE_TIME_FORMATTER));
            }
            model.addAttribute("scheduledUpdates", scheduledUpdates);
            model.addAttribute("formattedScheduledTimes", formattedScheduledTimes);
            
            return "config";
        } catch (Exception e) {
            model.addAttribute("error", "Error loading config: " + e.getMessage());
            return "error";
        }
    }

    @PostMapping(value = "/configs/{systemName}", params = "action=update")
    public String updateConfig(@PathVariable String systemName,
                               @RequestParam Map<String, String> allParams) {
        try {
            String decodedName = URLDecoder.decode(systemName, StandardCharsets.UTF_8);
            
            // РР·РІР»РµРєР°РµРј delays
            Map<String, String> delays = new HashMap<>();
            allParams.forEach((key, value) -> {
                if (key.startsWith("delay_")) {
                    delays.put(key.substring(6), value);
                }
            });
            
            // РР·РІР»РµРєР°РµРј stringParams
            Map<String, String> stringParams = new HashMap<>();
            allParams.forEach((key, value) -> {
                if (key.startsWith("string_")) {
                    stringParams.put(key.substring(7), value);
                }
            });
            
            // Извлекаем loggingLv
            String loggingLv = allParams.get("loggingLv");
            
            boolean hasChanges = configService.updateConfigFromForm(decodedName, delays, stringParams, loggingLv);
            if (!hasChanges) {
                return "redirect:/configs/" + java.net.URLEncoder.encode(decodedName, StandardCharsets.UTF_8) + 
                       "?info=" + java.net.URLEncoder.encode("Изменений не было, конфиг не обновлен", StandardCharsets.UTF_8);
            }
            return "redirect:/configs/" + java.net.URLEncoder.encode(decodedName, StandardCharsets.UTF_8) + "?saved=true";
        } catch (Exception e) {
            return "redirect:/configs/" + systemName + "?error=" + 
                   java.net.URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @PostMapping(value = "/configs/{systemName}", params = "action=revert")
    public String revertConfig(@PathVariable String systemName) {
        try {
            String decodedName = URLDecoder.decode(systemName, StandardCharsets.UTF_8);
            boolean hasChanges = configService.revertToStart(decodedName);
            if (!hasChanges) {
                return "redirect:/configs/" + java.net.URLEncoder.encode(decodedName, StandardCharsets.UTF_8) + 
                       "?info=" + java.net.URLEncoder.encode("Конфиг уже соответствует стартовому, изменений не было", StandardCharsets.UTF_8);
            }
            return "redirect:/configs/" + java.net.URLEncoder.encode(decodedName, StandardCharsets.UTF_8) + "?saved=true";
        } catch (Exception e) {
            return "redirect:/configs/" + systemName + "?error=" + 
                   java.net.URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @PostMapping(value = "/configs/{systemName}", params = "action=cancelSchedule")
    public String cancelScheduledUpdate(@PathVariable String systemName,
                                      @RequestParam String updateId) {
        try {
            String decodedName = URLDecoder.decode(systemName, StandardCharsets.UTF_8);
            scheduledConfigService.cancelScheduledUpdate(updateId);
            return "redirect:/configs/" + java.net.URLEncoder.encode(decodedName, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "redirect:/configs/" + systemName + "?error=" + 
                   java.net.URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @PostMapping(value = "/configs/{systemName}", params = "action=clearAllScheduled")
    public String clearAllScheduledUpdates(@PathVariable String systemName) {
        try {
            String decodedName = URLDecoder.decode(systemName, StandardCharsets.UTF_8);
            List<ScheduledConfigUpdate> updates = scheduledConfigService.getScheduledUpdates(decodedName);
            for (ScheduledConfigUpdate update : updates) {
                scheduledConfigService.cancelScheduledUpdate(update.getId());
            }
            return "redirect:/configs/" + java.net.URLEncoder.encode(decodedName, StandardCharsets.UTF_8) + 
                   "?info=" + java.net.URLEncoder.encode("Все запланированные обновления удалены", StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "redirect:/configs/" + systemName + "?error=" + 
                   java.net.URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @PostMapping(value = "/configs/{systemName}", params = "action=delete")
    public String deleteConfig(@PathVariable String systemName) {
        try {
            String decodedName = URLDecoder.decode(systemName, StandardCharsets.UTF_8);
            // Сначала получаем список запланированных обновлений до удаления конфига
            List<ScheduledConfigUpdate> updates = scheduledConfigService.getScheduledUpdates(decodedName);
            // Удаляем конфиг
            configService.deleteConfig(decodedName);
            // Удаляем все запланированные обновления для этой системы
            for (ScheduledConfigUpdate update : updates) {
                scheduledConfigService.cancelScheduledUpdate(update.getId());
            }
            return "redirect:/?info=" + java.net.URLEncoder.encode("Конфиг '" + decodedName + "' успешно удален", StandardCharsets.UTF_8);
        } catch (Exception e) {
            // При ошибке редиректим на главную страницу, так как конфиг может быть уже удален
            return "redirect:/?error=" + 
                   java.net.URLEncoder.encode("Ошибка при удалении конфига: " + e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @PostMapping(value = "/configs/{systemName}", params = "action=schedule")
    public String scheduleConfig(@PathVariable String systemName,
                                @RequestParam Map<String, String> allParams) {
        try {
            String decodedName = URLDecoder.decode(systemName, StandardCharsets.UTF_8);
            
            // Извлекаем дату и время
            String scheduledDateTimeStr = allParams.get("scheduledDateTime");
            if (scheduledDateTimeStr == null || scheduledDateTimeStr.isEmpty()) {
                return "redirect:/configs/" + java.net.URLEncoder.encode(decodedName, StandardCharsets.UTF_8) + 
                       "?error=" + java.net.URLEncoder.encode("Не указана дата и время", StandardCharsets.UTF_8);
            }
            
            LocalDateTime scheduledTime;
            try {
                scheduledTime = LocalDateTime.parse(scheduledDateTimeStr, DATE_TIME_FORMATTER);
            } catch (DateTimeParseException e) {
                return "redirect:/configs/" + java.net.URLEncoder.encode(decodedName, StandardCharsets.UTF_8) + 
                       "?error=" + java.net.URLEncoder.encode("Неверный формат даты. Используйте: hh:mm:ss DD-MM-YYYY", StandardCharsets.UTF_8);
            }
            
            if (scheduledTime.isBefore(LocalDateTime.now())) {
                return "redirect:/configs/" + java.net.URLEncoder.encode(decodedName, StandardCharsets.UTF_8) + 
                       "?error=" + java.net.URLEncoder.encode("Дата должна быть в будущем", StandardCharsets.UTF_8);
            }
            
            // Извлекаем параметры формы
            Map<String, String> delays = new HashMap<>();
            allParams.forEach((key, value) -> {
                if (key.startsWith("delay_")) {
                    delays.put(key.substring(6), value);
                }
            });
            
            Map<String, String> stringParams = new HashMap<>();
            allParams.forEach((key, value) -> {
                if (key.startsWith("string_")) {
                    stringParams.put(key.substring(7), value);
                }
            });
            
            String loggingLv = allParams.get("loggingLv");
            
            // Извлекаем комментарий
            String comment = allParams.get("scheduleComment");
            if (comment != null) {
                comment = comment.trim();
                if (comment.isEmpty()) {
                    comment = null;
                }
            }
            
            // Создаем конфиг из параметров формы
            JsonNode newConfig = configService.createConfigFromForm(delays, stringParams, loggingLv);
            
            // Планируем обновление
            scheduledConfigService.scheduleUpdate(decodedName, newConfig, scheduledTime, comment);
            
            return "redirect:/configs/" + java.net.URLEncoder.encode(decodedName, StandardCharsets.UTF_8) + "?scheduled=true";
        } catch (Exception e) {
            return "redirect:/configs/" + systemName + "?error=" + 
                   java.net.URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }
}
