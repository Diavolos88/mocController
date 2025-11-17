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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ConfigPageController {

    private final ConfigService configService;
    private final ScheduledConfigService scheduledConfigService;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy");

    public ConfigPageController(ConfigService configService, ScheduledConfigService scheduledConfigService) {
        this.configService = configService;
        this.scheduledConfigService = scheduledConfigService;
    }

    @GetMapping("/")
    public String landing(Model model) {
        model.addAttribute("configs", configService.findAll());
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
            
            // РР·РІР»РµРєР°РµРј loggingLv
            String loggingLv = allParams.get("loggingLv");
            
            configService.updateConfigFromForm(decodedName, delays, stringParams, loggingLv);
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
            configService.revertToStart(decodedName);
            return "redirect:/configs/" + java.net.URLEncoder.encode(decodedName, StandardCharsets.UTF_8);
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
