package com.mockcontroller.controller;

import com.mockcontroller.model.Group;
import com.mockcontroller.service.GroupService;
import com.mockcontroller.service.ScenarioHistoryService;
import com.mockcontroller.util.DateTimeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ScenarioHistoryPageController {

    private final ScenarioHistoryService historyService;
    private final GroupService groupService;
    
    @Value("${app.faq.url:}")
    private String faqUrl;

    public ScenarioHistoryPageController(ScenarioHistoryService historyService, GroupService groupService) {
        this.historyService = historyService;
        this.groupService = groupService;
    }
    
    private String getFaqUrl() {
        return (faqUrl != null && !faqUrl.trim().isEmpty()) ? faqUrl.trim() : "";
    }

    @GetMapping("/history")
    public String historyPage(@RequestParam(required = false) String groupId, 
                              @RequestParam(defaultValue = "1") int page, 
                              Model model) {
        model.addAttribute("faqUrl", getFaqUrl());
        model.addAttribute("currentGroup", groupId);
        model.addAttribute("currentPage", page);
        
        List<ScenarioHistoryService.ScenarioHistoryGroup> allHistoryGroups = historyService.getHistoryByGroups();
        
        // Получаем все группы для вкладок
        List<Group> allGroups = groupService.findAll().stream().toList();
        
        // Если выбрана конкретная группа, фильтруем
        List<ScenarioHistoryService.ScenarioHistoryGroup> historyGroups;
        if (groupId != null && !groupId.isEmpty() && !groupId.equals("all")) {
            historyGroups = allHistoryGroups.stream()
                    .filter(group -> group.getGroupId().equals(groupId))
                    .toList();
        } else {
            historyGroups = allHistoryGroups;
        }
        
        // Собираем все записи в один список для пагинации
        List<ScenarioHistoryService.ScheduledUpdateInfo> allUpdates = new ArrayList<>();
        for (ScenarioHistoryService.ScenarioHistoryGroup group : historyGroups) {
            // Создаем копии с информацией о группе для отображения
            for (ScenarioHistoryService.ScheduledUpdateInfo info : group.getScheduled()) {
                allUpdates.add(info);
            }
            for (ScenarioHistoryService.ScheduledUpdateInfo info : group.getCompleted()) {
                allUpdates.add(info);
            }
        }
        
        // Константа: записей на странице
        int pageSize = 10;
        int totalItems = allUpdates.size();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        
        // Ограничиваем номер страницы
        if (page < 1) page = 1;
        if (page > totalPages && totalPages > 0) page = totalPages;
        
        // Вычисляем индексы для пагинации
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalItems);
        
        // Получаем записи для текущей страницы
        List<ScenarioHistoryService.ScheduledUpdateInfo> pagedUpdates = allUpdates.subList(
            Math.max(0, startIndex), 
            endIndex
        );
        
        // Группируем записи обратно по группам для отображения (только текущая страница)
        Map<String, ScenarioHistoryService.ScenarioHistoryGroup> pagedGroupsMap = new LinkedHashMap<>();
        final List<ScenarioHistoryService.ScenarioHistoryGroup> finalHistoryGroupsForMapping = historyGroups;
        
        // Создаем карту для быстрого поиска группы по ID обновления
        Map<String, String> updateIdToGroupId = new HashMap<>();
        Map<String, Boolean> updateIdToIsScheduled = new HashMap<>();
        Map<String, ScenarioHistoryService.ScenarioHistoryGroup> groupIdToGroup = new HashMap<>();
        
        for (ScenarioHistoryService.ScenarioHistoryGroup group : finalHistoryGroupsForMapping) {
            groupIdToGroup.put(group.getGroupId(), group);
            for (ScenarioHistoryService.ScheduledUpdateInfo info : group.getScheduled()) {
                updateIdToGroupId.put(info.getUpdate().getId(), group.getGroupId());
                updateIdToIsScheduled.put(info.getUpdate().getId(), true);
            }
            for (ScenarioHistoryService.ScheduledUpdateInfo info : group.getCompleted()) {
                updateIdToGroupId.put(info.getUpdate().getId(), group.getGroupId());
                updateIdToIsScheduled.put(info.getUpdate().getId(), false);
            }
        }
        
        for (ScenarioHistoryService.ScheduledUpdateInfo info : pagedUpdates) {
            String updateId = info.getUpdate().getId();
            String targetGroupId = updateIdToGroupId.get(updateId);
            Boolean isScheduled = updateIdToIsScheduled.get(updateId);
            
            if (targetGroupId != null && isScheduled != null) {
                final String finalTargetGroupId = targetGroupId;
                ScenarioHistoryService.ScenarioHistoryGroup targetGroup = pagedGroupsMap.get(finalTargetGroupId);
                if (targetGroup == null) {
                    // Находим оригинальную группу для копирования информации
                    ScenarioHistoryService.ScenarioHistoryGroup originalGroup = groupIdToGroup.get(finalTargetGroupId);
                    if (originalGroup != null) {
                        targetGroup = new ScenarioHistoryService.ScenarioHistoryGroup(
                            originalGroup.getGroupId(),
                            originalGroup.getGroupName(),
                            originalGroup.getGroupDescription()
                        );
                        pagedGroupsMap.put(finalTargetGroupId, targetGroup);
                    }
                }
                
                if (targetGroup != null) {
                    if (isScheduled) {
                        targetGroup.getScheduled().add(info);
                    } else {
                        targetGroup.getCompleted().add(info);
                    }
                }
            }
        }
        
        List<ScenarioHistoryService.ScenarioHistoryGroup> pagedHistoryGroups = new ArrayList<>(pagedGroupsMap.values());
        
        // Форматируем даты для отображения
        Map<String, String> formattedTimes = new HashMap<>();
        for (ScenarioHistoryService.ScenarioHistoryGroup group : pagedHistoryGroups) {
            for (ScenarioHistoryService.ScheduledUpdateInfo info : group.getScheduled()) {
                if (info.getUpdate().getScheduledTime() != null) {
                    formattedTimes.put(info.getUpdate().getId(), 
                        info.getUpdate().getScheduledTime().format(DateTimeUtils.DATE_TIME_FORMATTER));
                } else if (info.getUpdate().getAppliedAt() != null) {
                    formattedTimes.put(info.getUpdate().getId(), 
                        info.getUpdate().getAppliedAt().format(DateTimeUtils.DATE_TIME_FORMATTER));
                }
            }
            for (ScenarioHistoryService.ScheduledUpdateInfo info : group.getCompleted()) {
                if (info.getUpdate().getAppliedAt() != null) {
                    formattedTimes.put(info.getUpdate().getId(), 
                        info.getUpdate().getAppliedAt().format(DateTimeUtils.DATE_TIME_FORMATTER));
                } else if (info.getUpdate().getScheduledTime() != null) {
                    formattedTimes.put(info.getUpdate().getId(), 
                        info.getUpdate().getScheduledTime().format(DateTimeUtils.DATE_TIME_FORMATTER));
                }
            }
        }
        
        // Подготавливаем данные для вкладок групп (используем все группы для подсчета)
        final List<ScenarioHistoryService.ScenarioHistoryGroup> finalHistoryGroups = allHistoryGroups;
        List<GroupTabInfo> groupTabs = allGroups.stream()
                .map(group -> {
                    long count = finalHistoryGroups.stream()
                            .filter(hg -> hg.getGroupId().equals(group.getId()))
                            .flatMap(hg -> {
                                List<ScenarioHistoryService.ScheduledUpdateInfo> combined = new ArrayList<>();
                                combined.addAll(hg.getScheduled());
                                combined.addAll(hg.getCompleted());
                                return combined.stream();
                            })
                            .count();
                    return new GroupTabInfo(group.getId(), group.getName(), count);
                })
                .toList();
        
        model.addAttribute("historyGroups", pagedHistoryGroups);
        model.addAttribute("formattedTimes", formattedTimes);
        model.addAttribute("groupTabs", groupTabs);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("pageSize", pageSize);
        
        return "history";
    }
    
    public static class GroupTabInfo {
        private final String id;
        private final String name;
        private final long count;
        
        public GroupTabInfo(String id, String name, long count) {
            this.id = id;
            this.name = name;
            this.count = count;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public long getCount() { return count; }
    }
}

