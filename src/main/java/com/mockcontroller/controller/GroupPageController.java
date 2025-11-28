package com.mockcontroller.controller;

import com.mockcontroller.model.Group;
import com.mockcontroller.service.ConfigService;
import com.mockcontroller.service.GroupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @Value("${app.faq.url:}")
    private String faqUrl;

    public GroupPageController(GroupService groupService, ConfigService configService) {
        this.groupService = groupService;
        this.configService = configService;
    }

    private String getFaqUrl() {
        return (faqUrl != null && !faqUrl.trim().isEmpty()) ? faqUrl.trim() : "";
    }

    @GetMapping
    public String groupsPage(Model model) {
        Collection<Group> groups = groupService.findAll();
        model.addAttribute("groups", groups);
        model.addAttribute("faqUrl", getFaqUrl());
        return "groups";
    }

    @GetMapping("/new")
    public String newGroupPage(Model model) {
        // Получаем все доступные заглушки (полные имена)
        Collection<com.mockcontroller.model.StoredConfig> configs = configService.findAll();
        List<String> allMocks = new ArrayList<>();
        
        for (com.mockcontroller.model.StoredConfig config : configs) {
            String systemName = config.getSystemName();
            if (systemName != null && !systemName.isEmpty()) {
                allMocks.add(systemName);
            }
        }
        
        allMocks.sort(String::compareToIgnoreCase);
        
        model.addAttribute("systems", allMocks);
        model.addAttribute("faqUrl", getFaqUrl());
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

    @GetMapping("/{id}/edit")
    public String editGroupPage(@PathVariable String id, Model model) {
        try {
            Group group = groupService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Group not found: " + id));
            
            // Получаем все доступные заглушки (полные имена)
            Collection<com.mockcontroller.model.StoredConfig> configs = configService.findAll();
            List<String> allMocks = new ArrayList<>();
            
            for (com.mockcontroller.model.StoredConfig config : configs) {
                String systemName = config.getSystemName();
                if (systemName != null && !systemName.isEmpty()) {
                    allMocks.add(systemName);
                }
            }
            
            allMocks.sort(String::compareToIgnoreCase);
            
            model.addAttribute("group", group);
            model.addAttribute("systems", allMocks);
            model.addAttribute("faqUrl", getFaqUrl());
            return "group-edit";
        } catch (IllegalArgumentException e) {
            return "redirect:/groups?error=" + URLEncoder.encode("Группа не найдена", StandardCharsets.UTF_8);
        }
    }

    @PostMapping("/{id}")
    public String updateGroup(@PathVariable String id,
                             @RequestParam String name,
                             @RequestParam(required = false) String description,
                             @RequestParam(required = false) List<String> systemNames) {
        try {
            groupService.updateGroup(id, name, description, systemNames);
            return "redirect:/groups?info=" + URLEncoder.encode("Группа успешно обновлена", StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "redirect:/groups?error=" + URLEncoder.encode("Группа не найдена", StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "redirect:/groups?error=" + URLEncoder.encode("Ошибка при обновлении группы: " + e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteGroup(@PathVariable String id) {
        try {
            groupService.deleteGroup(id);
            return "redirect:/groups?info=" + URLEncoder.encode("Группа успешно удалена", StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "redirect:/groups?error=" + URLEncoder.encode("Группа не найдена", StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "redirect:/groups?error=" + URLEncoder.encode("Ошибка при удалении группы: " + e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @PostMapping("/{groupId}/remove-system")
    public String removeSystemFromGroup(@PathVariable String groupId,
                                       @RequestParam String systemName) {
        try {
            groupService.removeSystemFromGroup(groupId, systemName);
            return "redirect:/status?info=" + URLEncoder.encode("Заглушка успешно удалена из группы", StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "redirect:/status?error=" + URLEncoder.encode("Группа не найдена", StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "redirect:/status?error=" + URLEncoder.encode("Ошибка при удалении заглушки из группы: " + e.getMessage(), StandardCharsets.UTF_8);
        }
    }
}

