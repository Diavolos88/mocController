package com.mockcontroller.controller;

import com.mockcontroller.model.Group;
import com.mockcontroller.service.ConfigService;
import com.mockcontroller.service.GroupService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
@RequestMapping("/groups")
public class GroupPageController {

    private final GroupService groupService;
    private final ConfigService configService;

    public GroupPageController(GroupService groupService, ConfigService configService) {
        this.groupService = groupService;
        this.configService = configService;
    }

    @GetMapping
    public String groupsPage(Model model) {
        Collection<Group> groups = groupService.findAll();
        model.addAttribute("groups", groups);
        return "groups";
    }

    @GetMapping("/new")
    public String newGroupPage(Model model) {
        // Получаем все доступные системы из заглушек
        Collection<com.mockcontroller.model.StoredConfig> configs = configService.findAll();
        Set<String> allSystems = new HashSet<>();
        
        for (com.mockcontroller.model.StoredConfig config : configs) {
            String systemName = config.getSystemName();
            if (isValidTemplate(systemName)) {
                String systemPrefix = extractSystemPrefix(systemName);
                allSystems.add(systemPrefix);
            } else {
                allSystems.add(systemName);
            }
        }
        
        List<String> systems = new ArrayList<>(allSystems);
        systems.sort(String::compareToIgnoreCase);
        
        model.addAttribute("systems", systems);
        return "group-new";
    }

    @PostMapping
    public String createGroup(@RequestParam String name,
                             @RequestParam(required = false) String description,
                             @RequestParam(required = false) List<String> systemNames) {
        try {
            groupService.createGroup(name, description, systemNames);
            return "redirect:/groups?info=" + URLEncoder.encode("Группа успешно создана", StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "redirect:/groups?error=" + URLEncoder.encode("Ошибка при создании группы: " + e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteGroup(@org.springframework.web.bind.annotation.PathVariable String id) {
        try {
            groupService.deleteGroup(id);
            return "redirect:/groups?info=" + URLEncoder.encode("Группа успешно удалена", StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "redirect:/groups?error=" + URLEncoder.encode("Группа не найдена", StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "redirect:/groups?error=" + URLEncoder.encode("Ошибка при удалении группы: " + e.getMessage(), StandardCharsets.UTF_8);
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
}

