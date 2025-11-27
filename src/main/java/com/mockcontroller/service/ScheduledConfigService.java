package com.mockcontroller.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockcontroller.model.ScheduledConfigUpdate;
import com.mockcontroller.model.entity.ScheduledConfigUpdateEntity;
import com.mockcontroller.repository.ScheduledConfigUpdateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ScheduledConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledConfigService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy");
    
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
        String safeSystemName = systemName != null ? systemName : "";
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
        return repository.existsBySystemName(systemName);
    }

    public List<ScheduledConfigUpdate> getScheduledUpdates(String systemName) {
        if (systemName == null || systemName.trim().isEmpty()) {
            return List.of();
        }
        return repository.findBySystemNameOrderByScheduledTimeAsc(systemName).stream()
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
                configService.updateCurrentConfig(systemName, update.getNewConfig());
                if (update.getId() != null) {
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
            repository.deleteBySystemName(systemName);
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

    public static DateTimeFormatter getDateTimeFormatter() {
        return DATE_TIME_FORMATTER;
    }
}

