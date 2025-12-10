package com.mockcontroller.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.mockcontroller.dto.ConfigViewDto;
import com.mockcontroller.model.Group;
import com.mockcontroller.model.StoredConfig;
import com.mockcontroller.model.Template;
import com.mockcontroller.service.ConfigService;
import com.mockcontroller.service.GroupService;
import com.mockcontroller.service.ScheduledConfigService;
import com.mockcontroller.service.TemplateService;
import com.mockcontroller.util.DateTimeUtils;
import com.mockcontroller.util.SystemNameUtils;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ConfigPageController {

    private final ConfigService configService;
    private final ScheduledConfigService scheduledConfigService;
    private final TemplateService templateService;
    private final GroupService groupService;
    
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

    public ConfigPageController(ConfigService configService, ScheduledConfigService scheduledConfigService, TemplateService templateService, GroupService groupService) {
        this.configService = configService;
        this.scheduledConfigService = scheduledConfigService;
        this.templateService = templateService;
        this.groupService = groupService;
    }

    @GetMapping("/")
    public String landing(@RequestParam(required = false) String system, Model model) {
        List<StoredConfig> allConfigs = new ArrayList<>(configService.findAll());
        
        // Получаем все группы
        Collection<Group> allGroups = groupService.findAll();
        
        // Создаем карту для быстрого поиска заглушек по имени
        Map<String, StoredConfig> configsByName = new HashMap<>();
        for (StoredConfig config : allConfigs) {
            configsByName.put(config.getSystemName(), config);
        }
        
        // Создаем список объектов с группами и количеством для отображения
        List<SystemInfo> systemsWithCounts = new ArrayList<>();
        
        // Добавляем только группы из базы данных
        for (Group group : allGroups) {
            // Считаем количество заглушек из группы, которые есть в базе
            int count = (int) group.getSystemNames().stream()
                .filter(configsByName::containsKey)
                .count();
            if (count > 0) {
                systemsWithCounts.add(new SystemInfo(group.getName(), count, false));
            }
        }
        
        // Сортируем группы по имени
        systemsWithCounts.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        
        // Если указана система (группа), фильтруем
        if (system != null && !system.isEmpty()) {
            // Ищем группу с таким именем
            Group selectedGroup = allGroups.stream()
                .filter(g -> g.getName().equalsIgnoreCase(system))
                .findFirst()
                .orElse(null);
            
            if (selectedGroup != null) {
                // Показываем заглушки из выбранной группы
                List<StoredConfig> filteredConfigs = new ArrayList<>();
                for (String systemName : selectedGroup.getSystemNames()) {
                    StoredConfig config = configsByName.get(systemName);
                    if (config != null) {
                        filteredConfigs.add(config);
                    }
                }
                filteredConfigs.sort((a, b) -> a.getSystemName().compareToIgnoreCase(b.getSystemName()));
                model.addAttribute("configs", filteredConfigs);
                model.addAttribute("currentSystem", selectedGroup.getName());
            } else {
                // Группа не найдена
                model.addAttribute("configs", new ArrayList<>());
                model.addAttribute("currentSystem", system);
            }
        } else {
            // Показываем первую группу по умолчанию
            if (!systemsWithCounts.isEmpty()) {
                String firstGroupName = systemsWithCounts.get(0).getName();
                Group firstGroup = allGroups.stream()
                    .filter(g -> g.getName().equals(firstGroupName))
                    .findFirst()
                    .orElse(null);
                
                if (firstGroup != null) {
                    List<StoredConfig> configsToShow = new ArrayList<>();
                    for (String systemName : firstGroup.getSystemNames()) {
                        StoredConfig config = configsByName.get(systemName);
                        if (config != null) {
                            configsToShow.add(config);
                        }
                    }
                    configsToShow.sort((a, b) -> a.getSystemName().compareToIgnoreCase(b.getSystemName()));
                    model.addAttribute("configs", configsToShow);
                    model.addAttribute("currentSystem", firstGroup.getName());
                } else {
                    model.addAttribute("configs", new ArrayList<>());
                    model.addAttribute("currentSystem", null);
                }
            } else {
                // Нет групп
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
            
            // Добавляем текущий конфиг как JSON строку для использования в JavaScript
            try {
                String currentConfigJson = configService.toPrettyJson(config.getCurrentConfig());
                model.addAttribute("currentConfigJson", currentConfigJson);
            } catch (Exception e) {
                model.addAttribute("currentConfigJson", "{}");
            }
            
            // Получаем все запланированные обновления и форматируем даты
            // Используем санитизированное имя для консистентности (как в ConfigService)
            // ConfigService.findBySystemName использует sanitize внутри, поэтому используем то же имя
            String sanitizedName = SystemNameUtils.sanitize(decodedName);
            List<ScheduledConfigUpdate> scheduledUpdates = scheduledConfigService.getScheduledUpdates(sanitizedName);
            Map<String, String> formattedScheduledTimes = new HashMap<>();
            for (ScheduledConfigUpdate update : scheduledUpdates) {
                if (update.getScheduledTime() != null) {
                    formattedScheduledTimes.put(update.getId(), update.getScheduledTime().format(DateTimeUtils.DATE_TIME_FORMATTER));
                } else {
                    formattedScheduledTimes.put(update.getId(), "N/A");
                }
            }
            model.addAttribute("scheduledUpdates", scheduledUpdates);
            model.addAttribute("formattedScheduledTimes", formattedScheduledTimes);
            
            // Получаем шаблоны для текущей системы
            List<Template> templates = templateService.findBySystemName(decodedName);
            model.addAttribute("templates", templates);
            
            // Добавляем JSON конфиги шаблонов для JavaScript
            try {
                Map<String, String> templatesConfigJson = new HashMap<>();
                for (Template template : templates) {
                    if (template.getConfig() != null) {
                        String configJson = configService.toPrettyJson(template.getConfig());
                        templatesConfigJson.put(template.getId(), configJson);
                    }
                }
                model.addAttribute("templatesConfigJson", templatesConfigJson);
            } catch (Exception e) {
                model.addAttribute("templatesConfigJson", new HashMap<>());
            }
            
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
            
            // Извлекаем stringParams
            Map<String, String> stringParams = new HashMap<>();
            allParams.forEach((key, value) -> {
                if (key.startsWith("string_")) {
                    stringParams.put(key.substring(7), value);
                }
            });
            
            // Извлекаем intParams
            Map<String, String> intParams = new HashMap<>();
            allParams.forEach((key, value) -> {
                if (key.startsWith("int_")) {
                    intParams.put(key.substring(4), value);
                }
            });
            
            // Извлекаем booleanVariables
            Map<String, String> booleanVariables = new HashMap<>();
            allParams.forEach((key, value) -> {
                if (key.startsWith("boolean_")) {
                    booleanVariables.put(key.substring(8), value);
                }
            });
            
            // Извлекаем loggingLv
            String loggingLv = allParams.get("loggingLv");
            
            try {
                boolean hasChanges = configService.updateConfigFromForm(decodedName, delays, stringParams, intParams, booleanVariables, loggingLv);
                if (!hasChanges) {
                    return "redirect:/configs/" + java.net.URLEncoder.encode(decodedName, StandardCharsets.UTF_8) + 
                           "?info=" + java.net.URLEncoder.encode("Изменений не было, конфиг не обновлен", StandardCharsets.UTF_8);
                }
                return "redirect:/configs/" + java.net.URLEncoder.encode(decodedName, StandardCharsets.UTF_8) + "?saved=true";
            } catch (IllegalArgumentException e) {
                return "redirect:/configs/" + java.net.URLEncoder.encode(decodedName, StandardCharsets.UTF_8) + "?error=" + 
                       java.net.URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return "redirect:/configs/" + systemName + "?error=" + 
                   java.net.URLEncoder.encode("Ошибка при обновлении конфига: " + e.getMessage(), StandardCharsets.UTF_8);
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
            scheduledConfigService.deleteAllBySystemName(decodedName);
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
            // Удаляем все запланированные обновления для этой системы
            scheduledConfigService.deleteAllBySystemName(decodedName);
            // Удаляем конфиг
            configService.deleteConfig(decodedName);
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
                scheduledTime = LocalDateTime.parse(scheduledDateTimeStr, DateTimeUtils.DATE_TIME_FORMATTER);
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
            
            Map<String, String> intParams = new HashMap<>();
            allParams.forEach((key, value) -> {
                if (key.startsWith("int_")) {
                    intParams.put(key.substring(4), value);
                }
            });
            
            // Извлекаем booleanVariables
            Map<String, String> booleanVariables = new HashMap<>();
            allParams.forEach((key, value) -> {
                if (key.startsWith("boolean_")) {
                    booleanVariables.put(key.substring(8), value);
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
            JsonNode newConfig = configService.createConfigFromForm(delays, stringParams, intParams, booleanVariables, loggingLv);
            
            // Планируем обновление
            scheduledConfigService.scheduleUpdate(decodedName, newConfig, scheduledTime, comment);
            
            return "redirect:/configs/" + java.net.URLEncoder.encode(decodedName, StandardCharsets.UTF_8) + "?scheduled=true";
        } catch (Exception e) {
            return "redirect:/configs/" + systemName + "?error=" + 
                   java.net.URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }
}
