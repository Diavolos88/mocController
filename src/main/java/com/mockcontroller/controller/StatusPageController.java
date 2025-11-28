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
        
        // Получаем все зарегистрированные системы из конфигов
        Set<String> allRegisteredSystems = getAllRegisteredSystems();
        
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
        
        // Добавляем системы с 0 подов (которые зарегистрированы, но не имеют инстансов)
        for (String systemName : allRegisteredSystems) {
            if (!statusMap.containsKey(systemName)) {
                SystemStatusView view = new SystemStatusView(
                    systemName,
                    0,  // onlineCount
                    0,  // offlineCount
                    0,  // totalCount
                    null,  // lastHealthcheckTime
                    "Никогда",  // formattedTime
                    false  // isAnyOnline
                );
                statusMap.put(systemName, view);
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
                } else if (allRegisteredSystems.contains(systemName)) {
                    // Система зарегистрирована, но не имеет инстансов (0 подов)
                    SystemStatusView view = new SystemStatusView(
                        systemName,
                        0,  // onlineCount
                        0,  // offlineCount
                        0,  // totalCount
                        null,  // lastHealthcheckTime
                        "Никогда",  // formattedTime
                        false  // isAnyOnline
                    );
                    groupSystems.add(view);
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
    
    /**
     * Получает все зарегистрированные системы из конфигов.
     * Извлекает префикс системы для заглушек в формате "system-name-emulation-mock".
     */
    private Set<String> getAllRegisteredSystems() {
        Collection<StoredConfig> configs = configService.findAll();
        Set<String> systems = new HashSet<>();
        
        for (StoredConfig config : configs) {
            String systemName = config.getSystemName();
            if (isValidTemplate(systemName)) {
                String systemPrefix = extractSystemPrefix(systemName);
                systems.add(systemPrefix);
            } else {
                systems.add(systemName);
            }
        }
        
        return systems;
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

