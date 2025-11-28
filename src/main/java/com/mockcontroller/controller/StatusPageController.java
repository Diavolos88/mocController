package com.mockcontroller.controller;

import com.mockcontroller.model.Group;
import com.mockcontroller.service.GroupService;
import com.mockcontroller.service.MockStatusService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class StatusPageController {

    private final MockStatusService mockStatusService;
    private final GroupService groupService;
    
    @Value("${app.faq.url:}")
    private String faqUrl;
    
    private static final DateTimeFormatter DATE_TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH:mm:ss dd.MM.yyyy").withZone(ZoneId.systemDefault());

    public StatusPageController(MockStatusService mockStatusService, GroupService groupService) {
        this.mockStatusService = mockStatusService;
        this.groupService = groupService;
    }
    
    private String getFaqUrl() {
        return (faqUrl != null && !faqUrl.trim().isEmpty()) ? faqUrl.trim() : "";
    }

    @GetMapping("/status")
    public String statusPage(Model model) {
        // Добавляем faqUrl из конфигурации
        model.addAttribute("faqUrl", getFaqUrl());
        Map<String, MockStatusService.SystemStatus> allStatuses = mockStatusService.getAllSystemStatuses();
        Collection<Group> groups = groupService.findAll();
        
        // Создаем карту для быстрого поиска статусов по systemName
        Map<String, SystemStatusView> statusMap = new HashMap<>();
        for (MockStatusService.SystemStatus status : allStatuses.values()) {
            SystemStatusView view = new SystemStatusView(
                status.getSystemName(),
                status.getOnlineCount(),
                status.getOfflineCount(),
                status.getTotalCount(),
                status.getLastHealthcheckTime(),
                formatTime(status.getLastHealthcheckTime()),
                status.isAnyOnline()
            );
            statusMap.put(status.getSystemName(), view);
        }
        
        // Группируем статусы по группам
        List<GroupStatusView> groupStatuses = new ArrayList<>();
        
        // Обрабатываем существующие группы
        for (Group group : groups) {
            List<SystemStatusView> groupSystems = new ArrayList<>();
            int totalOnline = 0;
            int totalOffline = 0;
            int totalInstances = 0;
            
            for (String systemName : group.getSystemNames()) {
                SystemStatusView status = statusMap.get(systemName);
                if (status != null) {
                    groupSystems.add(status);
                    totalOnline += status.getOnlineCount();
                    totalOffline += status.getOfflineCount();
                    totalInstances += status.getTotalCount();
                }
            }
            
            if (!groupSystems.isEmpty()) {
                GroupStatusView groupView = new GroupStatusView(
                    group.getId(),
                    group.getName(),
                    group.getDescription(),
                    groupSystems,
                    totalOnline,
                    totalOffline,
                    totalInstances
                );
                groupStatuses.add(groupView);
            }
        }
        
        // Добавляем системы без группы в отдельную группу "Без группы"
        Set<String> groupedSystems = groups.stream()
            .flatMap(g -> g.getSystemNames().stream())
            .collect(Collectors.toSet());
        
        List<SystemStatusView> ungroupedSystems = new ArrayList<>();
        int ungroupedOnline = 0;
        int ungroupedOffline = 0;
        int ungroupedInstances = 0;
        
        for (SystemStatusView status : statusMap.values()) {
            if (!groupedSystems.contains(status.getSystemName())) {
                ungroupedSystems.add(status);
                ungroupedOnline += status.getOnlineCount();
                ungroupedOffline += status.getOfflineCount();
                ungroupedInstances += status.getTotalCount();
            }
        }
        
        if (!ungroupedSystems.isEmpty()) {
            GroupStatusView ungroupedView = new GroupStatusView(
                null,
                "Без группы",
                "Системы, не входящие ни в одну группу",
                ungroupedSystems,
                ungroupedOnline,
                ungroupedOffline,
                ungroupedInstances
            );
            groupStatuses.add(ungroupedView);
        }
        
        model.addAttribute("groupStatuses", groupStatuses);
        return "status";
    }

    @GetMapping("/status/{systemName}")
    public String systemStatusPage(@PathVariable String systemName, Model model) {
        // Добавляем faqUrl из конфигурации
        model.addAttribute("faqUrl", getFaqUrl());
        var instances = mockStatusService.getInstancesBySystem(systemName);
        
        model.addAttribute("systemName", systemName);
        model.addAttribute("instances", instances.stream()
            .map(inst -> new InstanceStatusView(
                inst.getSystemName(),
                inst.getInstanceId(),
                inst.getLastHealthcheckTime(),
                formatTime(inst.getLastHealthcheckTime()),
                inst.isOnline()
            ))
            .toList());
        
        return "status-detail";
    }

    private String formatTime(Instant time) {
        if (time == null) {
            return "Никогда";
        }
        return DATE_TIME_FORMATTER.format(time);
    }

    public static class SystemStatusView {
        private final String systemName;
        private final int onlineCount;
        private final int offlineCount;
        private final int totalCount;
        private final Instant lastHealthcheckTime;
        private final String formattedTime;
        private final boolean isAnyOnline;

        public SystemStatusView(String systemName, int onlineCount, int offlineCount, 
                              int totalCount, Instant lastHealthcheckTime, 
                              String formattedTime, boolean isAnyOnline) {
            this.systemName = systemName;
            this.onlineCount = onlineCount;
            this.offlineCount = offlineCount;
            this.totalCount = totalCount;
            this.lastHealthcheckTime = lastHealthcheckTime;
            this.formattedTime = formattedTime;
            this.isAnyOnline = isAnyOnline;
        }

        public String getSystemName() { return systemName; }
        public int getOnlineCount() { return onlineCount; }
        public int getOfflineCount() { return offlineCount; }
        public int getTotalCount() { return totalCount; }
        public Instant getLastHealthcheckTime() { return lastHealthcheckTime; }
        public String getFormattedTime() { return formattedTime; }
        public boolean isAnyOnline() { return isAnyOnline; }
    }

    public static class InstanceStatusView {
        private final String systemName;
        private final String instanceId;
        private final Instant lastHealthcheckTime;
        private final String formattedTime;
        private final boolean isOnline;

        public InstanceStatusView(String systemName, String instanceId, 
                                 Instant lastHealthcheckTime, String formattedTime, 
                                 boolean isOnline) {
            this.systemName = systemName;
            this.instanceId = instanceId;
            this.lastHealthcheckTime = lastHealthcheckTime;
            this.formattedTime = formattedTime;
            this.isOnline = isOnline;
        }

        public String getSystemName() { return systemName; }
        public String getInstanceId() { return instanceId; }
        public Instant getLastHealthcheckTime() { return lastHealthcheckTime; }
        public String getFormattedTime() { return formattedTime; }
        public boolean isOnline() { return isOnline; }
    }

    public static class GroupStatusView {
        private final String groupId;
        private final String groupName;
        private final String groupDescription;
        private final List<SystemStatusView> systems;
        private final int totalOnline;
        private final int totalOffline;
        private final int totalInstances;

        public GroupStatusView(String groupId, String groupName, String groupDescription,
                              List<SystemStatusView> systems, int totalOnline, 
                              int totalOffline, int totalInstances) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.groupDescription = groupDescription;
            this.systems = systems;
            this.totalOnline = totalOnline;
            this.totalOffline = totalOffline;
            this.totalInstances = totalInstances;
        }

        public String getGroupId() { return groupId; }
        public String getGroupName() { return groupName; }
        public String getGroupDescription() { return groupDescription; }
        public List<SystemStatusView> getSystems() { return systems; }
        public int getTotalOnline() { return totalOnline; }
        public int getTotalOffline() { return totalOffline; }
        public int getTotalInstances() { return totalInstances; }
        public int getActiveSystemsCount() {
            return (int) systems.stream()
                .filter(SystemStatusView::isAnyOnline)
                .count();
        }
    }
}

