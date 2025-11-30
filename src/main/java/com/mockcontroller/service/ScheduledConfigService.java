package com.mockcontroller.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockcontroller.model.ScheduledConfigUpdate;
import com.mockcontroller.model.entity.ScheduledConfigUpdateEntity;
import com.mockcontroller.repository.ScheduledConfigUpdateRepository;
import com.mockcontroller.util.DateTimeUtils;
import com.mockcontroller.util.SystemNameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ScheduledConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledConfigService.class);
    
    private final ObjectMapper objectMapper;
    private final ConfigService configService;
    private final ScheduledConfigUpdateRepository repository;
    private final ScheduledConfigMapper mapper;

    public ScheduledConfigService(ObjectMapper objectMapper, ConfigService configService,
                                  ScheduledConfigUpdateRepository repository, ScheduledConfigMapper mapper) {
        this.objectMapper = objectMapper;
        this.configService = configService;
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public ScheduledConfigUpdate scheduleUpdate(String systemName, JsonNode newConfig, LocalDateTime scheduledTime) {
        return scheduleUpdate(systemName, newConfig, scheduledTime, null);
    }

    @Transactional
    public ScheduledConfigUpdate scheduleUpdate(String systemName, JsonNode newConfig, LocalDateTime scheduledTime, String comment) {
        if (newConfig == null) {
            throw new IllegalArgumentException("newConfig cannot be null");
        }
        if (scheduledTime == null) {
            throw new IllegalArgumentException("scheduledTime cannot be null");
        }
        // Используем санитизированное имя для консистентности с ConfigService
        String safeSystemName = SystemNameUtils.sanitize(systemName != null ? systemName : "");
        String newConfigJson = jsonToString(newConfig);
        
        // Проверяем на дубликаты: ищем существующие обновления для той же системы и времени
        List<ScheduledConfigUpdateEntity> existingUpdates = repository.findBySystemNameAndScheduledTime(safeSystemName, scheduledTime);
        
        for (ScheduledConfigUpdateEntity existing : existingUpdates) {
            // Сравниваем JSON конфиги (нормализуем для корректного сравнения)
            if (jsonConfigsEqual(existing.getNewConfigJson(), newConfigJson)) {
                // Найден дубликат - обновляем комментарий существующего и удаляем остальные дубликаты
                String mergedComment = mergeComments(existing.getComment(), comment);
                existing.setComment(mergedComment);
                
                // Удаляем все остальные дубликаты с таким же конфигом и временем
                for (ScheduledConfigUpdateEntity duplicate : existingUpdates) {
                    if (!duplicate.getId().equals(existing.getId()) && 
                        jsonConfigsEqual(duplicate.getNewConfigJson(), newConfigJson)) {
                        repository.deleteById(duplicate.getId());
                        logger.debug("Removed duplicate scheduled update {} for {} at {}", 
                            duplicate.getId(), safeSystemName, scheduledTime);
                    }
                }
                
                ScheduledConfigUpdateEntity updatedEntity = repository.save(existing);
                logger.debug("Duplicate scheduled update found for {} at {}. Merged comments: {}", 
                    safeSystemName, scheduledTime, mergedComment);
                return mapper.toModel(updatedEntity);
            }
        }
        
        // Дубликатов не найдено - создаем новое обновление
        ScheduledConfigUpdateEntity entity = new ScheduledConfigUpdateEntity(
            safeSystemName, 
            newConfigJson, 
            scheduledTime, 
            comment);
        entity = repository.save(entity);
        return mapper.toModel(entity);
    }
    
    /**
     * Сравнивает два JSON конфига (строковых представления) на равенство
     */
    private boolean jsonConfigsEqual(String json1, String json2) {
        if (json1 == null && json2 == null) {
            return true;
        }
        if (json1 == null || json2 == null) {
            return false;
        }
        try {
            JsonNode node1 = objectMapper.readTree(json1);
            JsonNode node2 = objectMapper.readTree(json2);
            // Нормализуем JSON для корректного сравнения
            String normalized1 = objectMapper.writeValueAsString(node1);
            String normalized2 = objectMapper.writeValueAsString(node2);
            return normalized1.equals(normalized2);
        } catch (Exception e) {
            // Если не удалось распарсить - сравниваем как строки
            return json1.equals(json2);
        }
    }
    
    /**
     * Объединяет два комментария, избегая дублирования
     */
    private String mergeComments(String existingComment, String newComment) {
        if (existingComment == null || existingComment.trim().isEmpty()) {
            return newComment != null ? newComment : null;
        }
        if (newComment == null || newComment.trim().isEmpty()) {
            return existingComment;
        }
        
        // Если комментарии одинаковые - возвращаем один
        if (existingComment.equals(newComment)) {
            return existingComment;
        }
        
        // Объединяем комментарии через разделитель
        return existingComment + " | " + newComment;
    }
    
    public boolean hasScheduledUpdate(String systemName) {
        if (systemName == null || systemName.trim().isEmpty()) {
            return false;
        }
        // Используем санитизированное имя для консистентности
        String safeSystemName = SystemNameUtils.sanitize(systemName);
        return repository.existsBySystemName(safeSystemName);
    }

    public List<ScheduledConfigUpdate> getScheduledUpdates(String systemName) {
        if (systemName == null || systemName.trim().isEmpty()) {
            return List.of();
        }
        // Используем санитизированное имя для консистентности
        String safeSystemName = SystemNameUtils.sanitize(systemName);
        LocalDateTime now = LocalDateTime.now();
        // Возвращаем только будущие обновления (которые еще не наступили)
        return repository.findBySystemNameOrderByScheduledTimeAsc(safeSystemName).stream()
                .filter(entity -> entity.getScheduledTime() != null && entity.getScheduledTime().isAfter(now))
                .map(mapper::toModel)
                .collect(Collectors.toList());
    }

    public ScheduledConfigUpdate getScheduledUpdate(String systemName) {
        return getScheduledUpdates(systemName).stream()
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public void cancelScheduledUpdate(String updateId) {
        if (updateId != null && repository.existsById(updateId)) {
            repository.deleteById(updateId);
        }
    }

    @Scheduled(fixedRate = 10000) // Проверка каждые 10 секунд
    @Transactional
    public void checkAndApplyScheduledUpdates() {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduledConfigUpdateEntity> dueUpdates = repository.findDueUpdates(now);

        // Группируем обновления по системе и времени для корректной обработки дубликатов
        // Используем LinkedHashMap для сохранения порядка (dueUpdates уже отсортированы по scheduledTime ASC)
        java.util.Map<String, List<ScheduledConfigUpdateEntity>> updatesByKey = new java.util.LinkedHashMap<>();
        for (ScheduledConfigUpdateEntity entity : dueUpdates) {
            String key = (entity.getSystemName() != null ? entity.getSystemName() : "unknown") + 
                         "@" + (entity.getScheduledTime() != null ? entity.getScheduledTime().toString() : "unknown");
            updatesByKey.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(entity);
        }

        // Применяем обновления в порядке scheduledTime (который сохраняется благодаря LinkedHashMap)
        for (java.util.Map.Entry<String, List<ScheduledConfigUpdateEntity>> entry : updatesByKey.entrySet()) {
            List<ScheduledConfigUpdateEntity> updates = entry.getValue();
            
            // Если несколько обновлений на одно время для одной системы - применяем последнее (самое новое)
            if (updates.size() > 1) {
                logger.warn("Found {} duplicate scheduled updates for key: {}. Will apply the latest one.", 
                    updates.size(), entry.getKey());
                // Сортируем по createdAt - берем последнее (самое новое)
                updates.sort((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return -1;
                    if (b.getCreatedAt() == null) return 1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                });
            }
            
            // Применяем обновление (или последнее из дубликатов)
            ScheduledConfigUpdateEntity entity = updates.get(0);
            ScheduledConfigUpdate update = mapper.toModel(entity);
            try {
                String systemName = update.getSystemName() != null ? update.getSystemName() : "unknown";
                JsonNode newConfig = update.getNewConfig();
                
                if (newConfig == null) {
                    logger.warn("Skipping scheduled update {} for {}: newConfig is null", 
                        update.getId(), systemName);
                    // Удаляем все обновления для этой системы и времени, даже с null конфигом
                    for (ScheduledConfigUpdateEntity e : updates) {
                        if (e.getId() != null) {
                            repository.deleteById(e.getId());
                        }
                    }
                    continue;
                }
                
                String commentInfo = update.getComment() != null ? " (comment: " + update.getComment() + ")" : "";
                logger.info("Applying scheduled update for {} at {} (scheduled: {}){}", 
                    systemName, now, entity.getScheduledTime(), commentInfo);
                configService.updateCurrentConfig(systemName, newConfig);
                
                // Удаляем все обновления для этой системы и времени после успешного применения
                for (ScheduledConfigUpdateEntity e : updates) {
                    if (e.getId() != null) {
                        repository.deleteById(e.getId());
                    }
                }
            } catch (Exception e) {
                String updateId = update.getId() != null ? update.getId() : "unknown";
                String systemName = update.getSystemName() != null ? update.getSystemName() : "unknown";
                logger.error("Failed to apply scheduled update {} for {}", 
                    updateId, systemName, e);
            }
        }
    }

    @Transactional
    public void deleteAllBySystemName(String systemName) {
        if (systemName != null && !systemName.trim().isEmpty()) {
            // Используем санитизированное имя для консистентности
            String safeSystemName = SystemNameUtils.sanitize(systemName);
            repository.deleteBySystemName(safeSystemName);
        }
    }

    private String jsonToString(JsonNode jsonNode) {
        if (jsonNode == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (Exception e) {
            return jsonNode.toString();
        }
    }

    public static java.time.format.DateTimeFormatter getDateTimeFormatter() {
        return DateTimeUtils.DATE_TIME_FORMATTER;
    }
}

