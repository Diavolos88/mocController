package com.mockcontroller.controller;

import com.mockcontroller.model.Group;
import com.mockcontroller.model.StoredConfig;
import com.mockcontroller.service.ConfigService;
import com.mockcontroller.service.GroupService;
import com.mockcontroller.service.MockStatusService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class StatusPageController {

    private final MockStatusService mockStatusService;
    private final GroupService groupService;
    private final ConfigService configService;
    
    @PostMapping("/status/{systemName}/remove")
    public String removeFromStatus(@PathVariable String systemName) {
        try {
            String decodedName = java.net.URLDecoder.decode(systemName, java.nio.charset.StandardCharsets.UTF_8);
            // Удаляем только healthcheck данные, конфиг остается
            mockStatusService.removeSystemFromStatus(decodedName);
            return "redirect:/status?info=" + 
                   java.net.URLEncoder.encode("Заглушка '" + decodedName + "' удалена из статусов", java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "redirect:/status?error=" + 
                   java.net.URLEncoder.encode("Ошибка при удалении из статусов: " + e.getMessage(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    
    @Value("${app.faq.url:}")
    private String faqUrl;
    
    private static final DateTimeFormatter DATE_TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH:mm:ss dd.MM.yyyy").withZone(ZoneId.systemDefault());

    public StatusPageController(MockStatusService mockStatusService, GroupService groupService, ConfigService configService) {
        this.mockStatusService = mockStatusService;
        this.groupService = groupService;
        this.configService = configService;
    }
    
    private String getFaqUrl() {
        return (faqUrl != null && !faqUrl.trim().isEmpty()) ? faqUrl.trim() : "";
    }

    @GetMapping("/status")
    public String statusPage(@org.springframework.web.bind.annotation.RequestParam(required = false) String groupId, Model model) {
        // Добавляем faqUrl из конфигурации
        model.addAttribute("faqUrl", getFaqUrl());
        Map<String, MockStatusService.SystemStatus> allStatuses = mockStatusService.getAllSystemStatuses();
        Collection<Group> groups = groupService.findAll();
        model.addAttribute("currentGroup", groupId);
        
        // Получаем все реальные заглушки из конфигов
        Collection<StoredConfig> allConfigs = configService.findAll();
        Map<String, StoredConfig> configsByName = new HashMap<>();
        for (StoredConfig config : allConfigs) {
            configsByName.put(config.getSystemName(), config);
        }
        
        // Создаем карту для быстрого поиска статусов по реальному имени заглушки
        Map<String, SystemStatusView> statusMap = new HashMap<>();
        for (MockStatusService.SystemStatus status : allStatuses.values()) {
            // Используем реальное имя заглушки из статуса
            String systemName = status.getSystemName();
            SystemStatusView view = new SystemStatusView(
                systemName,
                status.getOnlineCount(),
                status.getOfflineCount(),
                status.getTotalCount(),
                status.getLastHealthcheckTime(),
                formatTime(status.getLastHealthcheckTime()),
                status.isAnyOnline(),
                Arrays.asList(systemName) // Реальное имя конфига
            );
            statusMap.put(systemName, view);
        }
        
        // Добавляем заглушки с 0 подов, которые есть в группах
        // Используем только реальные имена заглушек, не префиксы
        Set<String> allGroupSystemNames = groups.stream()
            .flatMap(g -> g.getSystemNames().stream())
            .collect(Collectors.toSet());
        
        for (String systemName : configsByName.keySet()) {
            if (!statusMap.containsKey(systemName) && allGroupSystemNames.contains(systemName)) {
                // Показываем заглушку с 0 подов только если она в группе
                statusMap.put(systemName, createZeroPodsStatusView(systemName));
            }
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
                } else if (configsByName.containsKey(systemName)) {
                    // Заглушка зарегистрирована в конфигах, но не имеет инстансов (0 подов)
                    groupSystems.add(createZeroPodsStatusView(systemName));
                    // totalOnline, totalOffline, totalInstances остаются 0
                }
            }
            
            // Добавляем группу только если она содержит системы или если она выбрана
            if (!groupSystems.isEmpty() || (groupId != null && group.getId().equals(groupId))) {
                GroupStatusView groupView = new GroupStatusView(
                group.getId(),
                group.getName(),
                group.getDescription(),
                groupSystems,
                totalOnline,
                totalOffline,
                totalInstances,
                urlEncode(group.getName())
            );
            groupStatuses.add(groupView);
            }
        }
        
        // Добавляем системы без группы в отдельную группу "Без группы"
        // Показываем только заглушки с healthcheck (у которых есть инстансы), 
        // не показываем заглушки с 0 подов
        Set<String> groupedSystems = groups.stream()
            .flatMap(g -> g.getSystemNames().stream())
            .collect(Collectors.toSet());
        
        List<SystemStatusView> ungroupedSystems = new ArrayList<>();
        int ungroupedOnline = 0;
        int ungroupedOffline = 0;
        int ungroupedInstances = 0;
        
        for (SystemStatusView status : statusMap.values()) {
            // Показываем в "Без группы" только заглушки, которые:
            // 1. Не в группах
            // 2. Имеют хотя бы один инстанс (totalCount > 0) - то есть реально работают
            if (!groupedSystems.contains(status.getSystemName()) && status.getTotalCount() > 0) {
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
                ungroupedInstances,
                urlEncode("Без группы")
            );
            groupStatuses.add(ungroupedView);
        }
        
        // Если выбрана конкретная группа, фильтруем результаты
        if (groupId != null && !groupId.trim().isEmpty() && !"all".equals(groupId)) {
            List<GroupStatusView> filteredGroups = new ArrayList<>();
            for (GroupStatusView groupView : groupStatuses) {
                if (groupView.getGroupId() != null && groupView.getGroupId().equals(groupId)) {
                    filteredGroups.add(groupView);
                    break;
                } else if (groupView.getGroupId() == null && "ungrouped".equals(groupId)) {
                    filteredGroups.add(groupView);
                    break;
                }
            }
            model.addAttribute("groupStatuses", filteredGroups);
        } else {
            model.addAttribute("groupStatuses", groupStatuses);
        }
        
        // Создаем список групп для табов
        List<GroupTabInfo> groupTabs = new ArrayList<>();
        // Добавляем таб "Все" для показа всех групп
        groupTabs.add(new GroupTabInfo("all", "Все", groupId == null || groupId.isEmpty()));
        
        for (Group g : groups) {
            boolean hasSystems = groupStatuses.stream().anyMatch(gs -> gs.getGroupId() != null && gs.getGroupId().equals(g.getId()));
            if (hasSystems) {
                boolean isActive = g.getId().equals(groupId);
                groupTabs.add(new GroupTabInfo(g.getId(), g.getName(), isActive));
            }
        }
        // Добавляем таб "Без группы" если есть такие системы
        boolean hasUngrouped = groupStatuses.stream().anyMatch(gs -> gs.getGroupId() == null);
        if (hasUngrouped) {
            groupTabs.add(new GroupTabInfo("ungrouped", "Без группы", "ungrouped".equals(groupId)));
        }
        model.addAttribute("groupTabs", groupTabs);
        
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

    /**
     * Создает SystemStatusView для заглушки с нулевым количеством подов
     */
    private SystemStatusView createZeroPodsStatusView(String systemName) {
        return new SystemStatusView(
            systemName,
            0,  // onlineCount
            0,  // offlineCount
            0,  // totalCount
            null,  // lastHealthcheckTime
            "Никогда",  // formattedTime
            false,  // isAnyOnline
            Arrays.asList(systemName)
        );
    }
    
    private String formatTime(Instant time) {
        if (time == null) {
            return "Никогда";
        }
        return DATE_TIME_FORMATTER.format(time);
    }
    
    private String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value.replace(" ", "%20");
        }
    }
    
    

    public static class SystemStatusView {
        private final String systemName;
        private final int onlineCount;
        private final int offlineCount;
        private final int totalCount;
        private final Instant lastHealthcheckTime;
        private final String formattedTime;
        private final boolean isAnyOnline;
        private final List<String> configNames; // Реальные имена конфигов в базе

        public SystemStatusView(String systemName, int onlineCount, int offlineCount, 
                              int totalCount, Instant lastHealthcheckTime, 
                              String formattedTime, boolean isAnyOnline) {
            this(systemName, onlineCount, offlineCount, totalCount, lastHealthcheckTime, formattedTime, isAnyOnline, new ArrayList<>());
        }

        public SystemStatusView(String systemName, int onlineCount, int offlineCount, 
                              int totalCount, Instant lastHealthcheckTime, 
                              String formattedTime, boolean isAnyOnline, List<String> configNames) {
            this.systemName = systemName;
            this.onlineCount = onlineCount;
            this.offlineCount = offlineCount;
            this.totalCount = totalCount;
            this.lastHealthcheckTime = lastHealthcheckTime;
            this.formattedTime = formattedTime;
            this.isAnyOnline = isAnyOnline;
            this.configNames = configNames != null ? configNames : new ArrayList<>();
        }

        public String getSystemName() { return systemName; }
        public int getOnlineCount() { return onlineCount; }
        public int getOfflineCount() { return offlineCount; }
        public int getTotalCount() { return totalCount; }
        public Instant getLastHealthcheckTime() { return lastHealthcheckTime; }
        public String getFormattedTime() { return formattedTime; }
        public boolean isAnyOnline() { return isAnyOnline; }
        public List<String> getConfigNames() { return configNames; }
        public String getFirstConfigName() { return configNames.isEmpty() ? systemName : configNames.get(0); }
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
        private final String encodedGroupName;

        public GroupStatusView(String groupId, String groupName, String groupDescription,
                              List<SystemStatusView> systems, int totalOnline, 
                              int totalOffline, int totalInstances, String encodedGroupName) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.groupDescription = groupDescription;
            this.systems = systems;
            this.totalOnline = totalOnline;
            this.totalOffline = totalOffline;
            this.totalInstances = totalInstances;
            this.encodedGroupName = encodedGroupName;
        }

        public String getGroupId() { return groupId; }
        public String getGroupName() { return groupName; }
        public String getGroupDescription() { return groupDescription; }
        public List<SystemStatusView> getSystems() { return systems; }
        public int getTotalOnline() { return totalOnline; }
        public int getTotalOffline() { return totalOffline; }
        public int getTotalInstances() { return totalInstances; }
        public String getEncodedGroupName() { return encodedGroupName; }
        public int getActiveSystemsCount() {
            return (int) systems.stream()
                .filter(SystemStatusView::isAnyOnline)
                .count();
        }
    }
    
    public static class GroupTabInfo {
        private final String groupId;
        private final String groupName;
        private final boolean isActive;
        
        public GroupTabInfo(String groupId, String groupName, boolean isActive) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.isActive = isActive;
        }
        
        public String getGroupId() { return groupId; }
        public String getGroupName() { return groupName; }
        public boolean isActive() { return isActive; }
    }
}

