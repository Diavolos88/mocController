package com.mockcontroller.controller;

import com.mockcontroller.dto.ConfigViewDto;
import com.mockcontroller.model.StoredConfig;
import com.mockcontroller.service.ConfigService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Controller
public class ConfigPageController {

    private final ConfigService configService;

    public ConfigPageController(ConfigService configService) {
        this.configService = configService;
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
}
