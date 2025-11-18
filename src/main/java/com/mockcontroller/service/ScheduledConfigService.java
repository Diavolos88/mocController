package com.mockcontroller.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockcontroller.model.ScheduledConfigUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ScheduledConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledConfigService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy");
    
    private final ObjectMapper objectMapper;
    private final ConfigService configService;
    private final Map<String, ScheduledConfigUpdate> scheduledUpdates = new ConcurrentHashMap<>();
    private final Path storageDir = Path.of("data", "scheduled");
    private final ReentrantLock lock = new ReentrantLock();

    public ScheduledConfigService(ObjectMapper objectMapper, ConfigService configService) {
        this.objectMapper = objectMapper;
        this.configService = configService;
        initStorage();
        loadScheduledUpdates();
    }

    private void initStorage() {
        try {
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }
        } catch (IOException e) {
            logger.error("Failed to create scheduled configs directory", e);
        }
    }

    public void scheduleUpdate(String systemName, JsonNode newConfig, LocalDateTime scheduledTime) {
        scheduleUpdate(systemName, newConfig, scheduledTime, null);
    }

    public ScheduledConfigUpdate scheduleUpdate(String systemName, JsonNode newConfig, LocalDateTime scheduledTime, String comment) {
        lock.lock();
        try {
            ScheduledConfigUpdate update = new ScheduledConfigUpdate(systemName, newConfig, scheduledTime, comment);
            scheduledUpdates.put(update.getId(), update);
            persistScheduledUpdate(update);
            logger.info("Scheduled config update for {} at {} (id: {}, comment: {})", systemName, scheduledTime, update.getId(), comment);
            return update;
        } finally {
            lock.unlock();
        }
    }

    public boolean hasScheduledUpdate(String systemName) {
        return scheduledUpdates.values().stream()
                .anyMatch(update -> update.getSystemName().equals(systemName));
    }

    public List<ScheduledConfigUpdate> getScheduledUpdates(String systemName) {
        return scheduledUpdates.values().stream()
                .filter(update -> update.getSystemName().equals(systemName))
                .sorted((a, b) -> a.getScheduledTime().compareTo(b.getScheduledTime()))
                .toList();
    }

    public ScheduledConfigUpdate getScheduledUpdate(String systemName) {
        // Для обратной совместимости возвращаем первое обновление
        return getScheduledUpdates(systemName).stream()
                .findFirst()
                .orElse(null);
    }

    public void cancelScheduledUpdate(String updateId) {
        lock.lock();
        try {
            ScheduledConfigUpdate update = scheduledUpdates.remove(updateId);
            if (update != null) {
                deleteScheduledUpdateFile(update);
                logger.info("Cancelled scheduled update {} for {}", updateId, update.getSystemName());
            }
        } finally {
            lock.unlock();
        }
    }

    @Scheduled(fixedRate = 10000) // Проверка каждые 10 секунд
    public void checkAndApplyScheduledUpdates() {
        lock.lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            List<String> toRemove = new ArrayList<>();

            for (ScheduledConfigUpdate update : scheduledUpdates.values()) {
                if (update.getScheduledTime().isBefore(now) || update.getScheduledTime().isEqual(now)) {
                    try {
                        logger.info("Applying scheduled update {} for {} at {}", update.getId(), update.getSystemName(), now);
                        configService.updateCurrentConfig(update.getSystemName(), update.getNewConfig());
                        toRemove.add(update.getId());
                        logger.info("Successfully applied scheduled update {} for {}", update.getId(), update.getSystemName());
                    } catch (Exception e) {
                        logger.error("Failed to apply scheduled update {} for {}", update.getId(), update.getSystemName(), e);
                    }
                }
            }

            // Удаляем примененные обновления
            for (String updateId : toRemove) {
                ScheduledConfigUpdate update = scheduledUpdates.remove(updateId);
                if (update != null) {
                    deleteScheduledUpdateFile(update);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void persistScheduledUpdate(ScheduledConfigUpdate update) {
        try {
            Path file = storageDir.resolve(update.getId() + ".json");
            objectMapper.writeValue(file.toFile(), update);
        } catch (IOException e) {
            logger.error("Failed to persist scheduled update {} for {}", update.getId(), update.getSystemName(), e);
        }
    }

    private void deleteScheduledUpdateFile(ScheduledConfigUpdate update) {
        try {
            Path file = storageDir.resolve(update.getId() + ".json");
            Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.error("Failed to delete scheduled update file {} for {}", update.getId(), update.getSystemName(), e);
        }
    }

    private void loadScheduledUpdates() {
        try {
            if (!Files.exists(storageDir)) {
                return;
            }

            Files.list(storageDir)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            ScheduledConfigUpdate update = objectMapper.readValue(path.toFile(), ScheduledConfigUpdate.class);
                            // Если ID отсутствует (старый формат), генерируем новый
                            if (update.getId() == null || update.getId().isEmpty()) {
                                update.setId(java.util.UUID.randomUUID().toString());
                                // Удаляем старый файл (без ID в имени)
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    logger.warn("Failed to delete old format file {}", path, e);
                                }
                                // Сохраняем с новым ID
                                persistScheduledUpdate(update);
                            }
                            // Загружаем только будущие обновления
                            if (update.getScheduledTime().isAfter(LocalDateTime.now())) {
                                scheduledUpdates.put(update.getId(), update);
                                logger.info("Loaded scheduled update {} for {} at {}", 
                                    update.getId(), update.getSystemName(), update.getScheduledTime());
                            } else {
                                // Удаляем просроченные обновления
                                deleteScheduledUpdateFile(update);
                            }
                        } catch (IOException e) {
                            logger.error("Failed to load scheduled update from {}", path, e);
                        }
                    });
        } catch (IOException e) {
            logger.error("Failed to load scheduled updates", e);
        }
    }


    public static DateTimeFormatter getDateTimeFormatter() {
        return DATE_TIME_FORMATTER;
    }
}

