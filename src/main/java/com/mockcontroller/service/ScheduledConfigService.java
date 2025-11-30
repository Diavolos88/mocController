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
        ScheduledConfigUpdateEntity entity = new ScheduledConfigUpdateEntity(
            safeSystemName, 
            jsonToString(newConfig), 
            scheduledTime, 
            comment);
        entity = repository.save(entity);
        return mapper.toModel(entity);
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

        for (ScheduledConfigUpdateEntity entity : dueUpdates) {
            ScheduledConfigUpdate update = mapper.toModel(entity);
            try {
                String systemName = update.getSystemName() != null ? update.getSystemName() : "unknown";
                JsonNode newConfig = update.getNewConfig();
                
                if (newConfig == null) {
                    logger.warn("Skipping scheduled update {} for {}: newConfig is null", 
                        update.getId(), systemName);
                    continue;
                }
                
                logger.info("Applying scheduled update for {} at {} (scheduled: {})", 
                    systemName, now, entity.getScheduledTime());
                configService.updateCurrentConfig(systemName, newConfig);
                if (update.getId() != null) {
                    logger.info("Deleting scheduled update {} after successful application", update.getId());
                    repository.deleteById(update.getId());
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

