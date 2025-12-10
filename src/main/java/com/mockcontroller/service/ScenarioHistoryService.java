package com.mockcontroller.service;

import com.mockcontroller.model.Group;
import com.mockcontroller.model.Scenario;
import com.mockcontroller.model.ScheduledConfigUpdate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScenarioHistoryService {

    private final ScheduledConfigService scheduledConfigService;
    private final ScenarioService scenarioService;
    private final GroupService groupService;

    public ScenarioHistoryService(ScheduledConfigService scheduledConfigService,
                                 ScenarioService scenarioService,
                                 GroupService groupService) {
        this.scheduledConfigService = scheduledConfigService;
        this.scenarioService = scenarioService;
        this.groupService = groupService;
    }

    /**
     * Группа истории сценариев
     */
    public static class ScenarioHistoryGroup {
        private final String groupId;
        private final String groupName;
        private final String groupDescription;
        private final List<ScheduledUpdateInfo> scheduled; // Запланированные (от ближайшего к дальним)
        private final List<ScheduledUpdateInfo> completed; // Выполненные (от последнего к старым)

        public ScenarioHistoryGroup(String groupId, String groupName, String groupDescription) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.groupDescription = groupDescription;
            this.scheduled = new ArrayList<>();
            this.completed = new ArrayList<>();
        }

        public String getGroupId() { return groupId; }
        public String getGroupName() { return groupName; }
        public String getGroupDescription() { return groupDescription; }
        public List<ScheduledUpdateInfo> getScheduled() { return scheduled; }
        public List<ScheduledUpdateInfo> getCompleted() { return completed; }
    }

    /**
     * Информация об обновлении со сценарием
     */
    public static class ScheduledUpdateInfo {
        private final ScheduledConfigUpdate update;
        private final String scenarioName;
        private final String stepComment;

        public ScheduledUpdateInfo(ScheduledConfigUpdate update, String scenarioName, String stepComment) {
            this.update = update;
            this.scenarioName = scenarioName;
            this.stepComment = stepComment;
        }

        public ScheduledConfigUpdate getUpdate() { return update; }
        public String getScenarioName() { return scenarioName; }
        public String getStepComment() { return stepComment; }
    }

    /**
     * Получает историю сценариев, сгруппированную по группам
     */
    public List<ScenarioHistoryGroup> getHistoryByGroups() {
        List<ScheduledConfigUpdate> allUpdates = scheduledConfigService.getAllScenarioUpdates();
        LocalDateTime now = LocalDateTime.now();
        
        // Извлекаем информацию о сценариях из комментариев
        List<ScheduledUpdateInfo> updateInfos = allUpdates.stream()
                .map(this::extractScenarioInfo)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        // Разделяем на запланированные и выполненные
        // Запланированные: не примененные и время еще не наступило
        List<ScheduledUpdateInfo> scheduled = updateInfos.stream()
                .filter(info -> {
                    Boolean applied = info.getUpdate().getApplied();
                    boolean isNotApplied = applied == null || !applied;
                    LocalDateTime scheduledTime = info.getUpdate().getScheduledTime();
                    return isNotApplied && scheduledTime != null && scheduledTime.isAfter(now);
                })
                .sorted(Comparator.comparing(info -> info.getUpdate().getScheduledTime()))
                .collect(Collectors.toList());
        
        // Выполненные: примененные или время уже прошло (но только примененные для истории)
        List<ScheduledUpdateInfo> completed = updateInfos.stream()
                .filter(info -> {
                    Boolean applied = info.getUpdate().getApplied();
                    boolean isApplied = applied != null && applied;
                    // Показываем в выполненных только те, которые реально применены
                    return isApplied;
                })
                .sorted((info1, info2) -> {
                    LocalDateTime time1 = info1.getUpdate().getAppliedAt();
                    LocalDateTime time2 = info2.getUpdate().getAppliedAt();
                    if (time1 == null && time2 == null) {
                        // Если нет времени применения, используем scheduledTime
                        time1 = info1.getUpdate().getScheduledTime();
                        time2 = info2.getUpdate().getScheduledTime();
                    }
                    if (time1 == null && time2 == null) return 0;
                    if (time1 == null) return 1;
                    if (time2 == null) return -1;
                    return time2.compareTo(time1); // Обратный порядок: от нового к старому
                })
                .collect(Collectors.toList());
        
        // Получаем все сценарии и группы
        Collection<Scenario> scenarios = scenarioService.findAll();
        Collection<Group> groups = groupService.findAll();
        
        // Создаем карту: название сценария -> группа
        Map<String, String> scenarioToGroupId = new HashMap<>();
        Map<String, String> scenarioToGroupName = new HashMap<>();
        Map<String, String> scenarioToGroupDescription = new HashMap<>();
        
        for (Scenario scenario : scenarios) {
            String scenarioName = scenario.getName();
            String groupId = scenario.getGroupId();
            
            Optional<Group> groupOpt = groups.stream()
                    .filter(g -> g.getId().equals(groupId))
                    .findFirst();
            
            if (groupOpt.isPresent()) {
                Group group = groupOpt.get();
                scenarioToGroupId.put(scenarioName, groupId);
                scenarioToGroupName.put(scenarioName, group.getName());
                scenarioToGroupDescription.put(scenarioName, group.getDescription() != null ? group.getDescription() : "");
            }
        }
        
        // Группируем по группам
        Map<String, ScenarioHistoryGroup> groupsMap = new LinkedHashMap<>();
        
        // Обрабатываем запланированные
        for (ScheduledUpdateInfo info : scheduled) {
            String groupId = scenarioToGroupId.get(info.getScenarioName());
            if (groupId == null) {
                // Если группа не найдена, создаем группу "Без группы"
                groupId = "unknown";
                if (!groupsMap.containsKey(groupId)) {
                    groupsMap.put(groupId, new ScenarioHistoryGroup("unknown", "Без группы", ""));
                }
            } else {
                if (!groupsMap.containsKey(groupId)) {
                    String groupName = scenarioToGroupName.get(info.getScenarioName());
                    String groupDescription = scenarioToGroupDescription.get(info.getScenarioName());
                    groupsMap.put(groupId, new ScenarioHistoryGroup(groupId, groupName, groupDescription));
                }
            }
            groupsMap.get(groupId).getScheduled().add(info);
        }
        
        // Обрабатываем выполненные
        for (ScheduledUpdateInfo info : completed) {
            String groupId = scenarioToGroupId.get(info.getScenarioName());
            if (groupId == null) {
                groupId = "unknown";
                if (!groupsMap.containsKey(groupId)) {
                    groupsMap.put(groupId, new ScenarioHistoryGroup("unknown", "Без группы", ""));
                }
            } else {
                if (!groupsMap.containsKey(groupId)) {
                    String groupName = scenarioToGroupName.get(info.getScenarioName());
                    String groupDescription = scenarioToGroupDescription.get(info.getScenarioName());
                    groupsMap.put(groupId, new ScenarioHistoryGroup(groupId, groupName, groupDescription));
                }
            }
            groupsMap.get(groupId).getCompleted().add(info);
        }
        
        return new ArrayList<>(groupsMap.values());
    }
    
    /**
     * Извлекает информацию о сценарии из комментария
     */
    private ScheduledUpdateInfo extractScenarioInfo(ScheduledConfigUpdate update) {
        String comment = update.getComment();
        if (comment == null || !comment.contains("Сценарий:")) {
            return null;
        }
        
        String scenarioName = null;
        String stepComment = null;
        
        // Формат: "Сценарий: название|Комментарий ступени: комментарий"
        if (comment.contains("|")) {
            String[] parts = comment.split("\\|", 2);
            if (parts.length > 0 && parts[0].contains("Сценарий:")) {
                scenarioName = parts[0].substring(parts[0].indexOf("Сценарий:") + "Сценарий:".length()).trim();
            }
            if (parts.length > 1 && parts[1].contains("Комментарий ступени:")) {
                stepComment = parts[1].substring(parts[1].indexOf("Комментарий ступени:") + "Комментарий ступени:".length()).trim();
            }
        } else {
            // Только сценарий без комментария
            if (comment.contains("Сценарий:")) {
                scenarioName = comment.substring(comment.indexOf("Сценарий:") + "Сценарий:".length()).trim();
            }
        }
        
        if (scenarioName == null || scenarioName.isEmpty()) {
            return null;
        }
        
        return new ScheduledUpdateInfo(update, scenarioName, stepComment);
    }
}

