package com.mockcontroller.service;

import com.mockcontroller.model.entity.MockInstanceEntity;
import com.mockcontroller.repository.MockInstanceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
public class MockStatusService {

    private final MockInstanceRepository repository;
    
    @Value("${app.status.offline-threshold-seconds:300}")
    private int offlineThresholdSeconds;
    
    @Value("${app.status.stats-window-seconds:300}")
    private int statsWindowSeconds;
    
    @Value("${app.status.cleanup-threshold-seconds:3600}")
    private int cleanupThresholdSeconds;

    public MockStatusService(MockInstanceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registerHealthcheck(String systemName, String instanceId) {
        String safeSystemName = sanitize(systemName);
        String safeInstanceId = instanceId != null && !instanceId.trim().isEmpty() 
            ? instanceId.trim() 
            : "default";

        Optional<MockInstanceEntity> existing = repository.findBySystemNameAndInstanceId(
            safeSystemName, safeInstanceId);

        if (existing.isPresent()) {
            MockInstanceEntity entity = existing.get();
            entity.setLastHealthcheckTime(Instant.now());
            repository.save(entity);
        } else {
            MockInstanceEntity entity = new MockInstanceEntity(safeSystemName, safeInstanceId);
            entity.setLastHealthcheckTime(Instant.now());
            repository.save(entity);
        }
    }

    public Map<String, SystemStatus> getAllSystemStatuses() {
        // Получаем все инстансы из базы
        // Важно: если у заглушки все поды удалены (0 подов), она не попадет в результат
        // и не будет отображаться на странице статусов, но сама заглушка остается
        // зарегистрированной в системе (если была зарегистрирована через конфиги)
        List<MockInstanceEntity> allInstances = repository.findAllByOrderBySystemNameAsc();
        
        // Фильтруем инстансы: учитываем только те, которые отправляли healthcheck за последние statsWindowSeconds
        Instant statsWindowThreshold = Instant.now().minus(statsWindowSeconds, ChronoUnit.SECONDS);
        
        Map<String, SystemStatus> statusMap = new LinkedHashMap<>();
        
        for (MockInstanceEntity instance : allInstances) {
            // Пропускаем инстансы без healthcheck
            if (instance.getLastHealthcheckTime() == null) {
                continue;
            }
            // Пропускаем инстансы, которые не отправляли healthcheck за последние statsWindowSeconds
            if (instance.getLastHealthcheckTime().isBefore(statsWindowThreshold)) {
                continue;
            }
            
            String systemName = instance.getSystemName();
            SystemStatus status = statusMap.computeIfAbsent(systemName, 
                k -> new SystemStatus(systemName));
            
            boolean isOnline = isInstanceOnline(instance);
            if (isOnline) {
                status.incrementOnlineCount();
            } else {
                status.incrementOfflineCount();
            }
            
            if (status.getLastHealthcheckTime() == null || 
                instance.getLastHealthcheckTime().isAfter(status.getLastHealthcheckTime())) {
                status.setLastHealthcheckTime(instance.getLastHealthcheckTime());
            }
        }
        
        return statusMap;
    }

    public List<InstanceStatus> getInstancesBySystem(String systemName) {
        String safeSystemName = sanitize(systemName);
        List<MockInstanceEntity> instances = repository.findBySystemName(safeSystemName);
        
        return instances.stream()
            .map(instance -> {
                boolean isOnline = isInstanceOnline(instance);
                return new InstanceStatus(
                    instance.getSystemName(),
                    instance.getInstanceId(),
                    instance.getLastHealthcheckTime(),
                    isOnline
                );
            })
            .sorted(Comparator.comparing(InstanceStatus::getLastHealthcheckTime).reversed())
            .collect(Collectors.toList());
    }

    private boolean isInstanceOnline(MockInstanceEntity instance) {
        if (instance.getLastHealthcheckTime() == null) {
            return false;
        }
        Instant threshold = Instant.now().minus(offlineThresholdSeconds, ChronoUnit.SECONDS);
        return instance.getLastHealthcheckTime().isAfter(threshold);
    }

    @Scheduled(fixedRate = 60000) // Каждую минуту
    @Transactional
    public void cleanupOldInstances() {
        // Удаляем инстансы (поды) заглушек, которые не отправляли healthcheck дольше указанного времени
        // Удаляются именно инстансы, а не заглушки (системы)
        Instant threshold = Instant.now().minus(cleanupThresholdSeconds, ChronoUnit.SECONDS);
        repository.deleteOldInstances(threshold);
    }

    /**
     * Проверяет статус группы: есть ли хотя бы одна онлайн под для каждой заглушки в группе
     * @param groupSystems список систем в группе
     * @return результат проверки с детальной статистикой
     */
    public GroupHealthCheckResult checkGroupHealth(List<String> groupSystems) {
        List<SystemHealthInfo> systemsInfo = new ArrayList<>();
        boolean allSystemsHaveOnlinePods = true;
        
        // Фильтруем инстансы: учитываем только те, которые отправляли healthcheck за последние statsWindowSeconds
        Instant statsWindowThreshold = Instant.now().minus(statsWindowSeconds, ChronoUnit.SECONDS);
        
        for (String systemName : groupSystems) {
            String safeSystemName = sanitize(systemName);
            List<MockInstanceEntity> instances = repository.findBySystemName(safeSystemName);
            
            int onlineCount = 0;
            int offlineCount = 0;
            int totalCount = 0;
            
            for (MockInstanceEntity instance : instances) {
                // Пропускаем инстансы без healthcheck
                if (instance.getLastHealthcheckTime() == null) {
                    continue;
                }
                
                // Учитываем только инстансы, которые отправляли healthcheck за последние statsWindowSeconds
                if (instance.getLastHealthcheckTime().isBefore(statsWindowThreshold)) {
                    continue;
                }
                
                totalCount++;
                boolean isOnline = isInstanceOnline(instance);
                if (isOnline) {
                    onlineCount++;
                } else {
                    offlineCount++;
                }
            }
            
            boolean hasOnlinePods = onlineCount > 0;
            if (!hasOnlinePods) {
                allSystemsHaveOnlinePods = false;
            }
            
            systemsInfo.add(new SystemHealthInfo(systemName, onlineCount, offlineCount, totalCount, hasOnlinePods));
        }
        
        return new GroupHealthCheckResult(allSystemsHaveOnlinePods, systemsInfo);
    }
    
    public static class GroupHealthCheckResult {
        private final boolean allHealthy;
        private final List<SystemHealthInfo> systems;
        
        public GroupHealthCheckResult(boolean allHealthy, List<SystemHealthInfo> systems) {
            this.allHealthy = allHealthy;
            this.systems = systems;
        }
        
        public boolean isAllHealthy() {
            return allHealthy;
        }
        
        public List<SystemHealthInfo> getSystems() {
            return systems;
        }
    }
    
    public static class SystemHealthInfo {
        private final String systemName;
        private final int onlineCount;
        private final int offlineCount;
        private final int totalCount;
        private final boolean hasOnlinePods;
        
        public SystemHealthInfo(String systemName, int onlineCount, int offlineCount, int totalCount, boolean hasOnlinePods) {
            this.systemName = systemName;
            this.onlineCount = onlineCount;
            this.offlineCount = offlineCount;
            this.totalCount = totalCount;
            this.hasOnlinePods = hasOnlinePods;
        }
        
        public String getSystemName() {
            return systemName;
        }
        
        public int getOnlineCount() {
            return onlineCount;
        }
        
        public int getOfflineCount() {
            return offlineCount;
        }
        
        public int getTotalCount() {
            return totalCount;
        }
        
        public boolean isHasOnlinePods() {
            return hasOnlinePods;
        }
    }

    private String sanitize(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    public static class SystemStatus {
        private final String systemName;
        private int onlineCount = 0;
        private int offlineCount = 0;
        private Instant lastHealthcheckTime;

        public SystemStatus(String systemName) {
            this.systemName = systemName;
        }

        public String getSystemName() {
            return systemName;
        }

        public int getOnlineCount() {
            return onlineCount;
        }

        public void incrementOnlineCount() {
            this.onlineCount++;
        }

        public int getOfflineCount() {
            return offlineCount;
        }

        public void incrementOfflineCount() {
            this.offlineCount++;
        }

        public int getTotalCount() {
            return onlineCount + offlineCount;
        }

        public Instant getLastHealthcheckTime() {
            return lastHealthcheckTime;
        }

        public void setLastHealthcheckTime(Instant lastHealthcheckTime) {
            this.lastHealthcheckTime = lastHealthcheckTime;
        }

        public boolean isAnyOnline() {
            return onlineCount > 0;
        }
    }

    public static class InstanceStatus {
        private final String systemName;
        private final String instanceId;
        private final Instant lastHealthcheckTime;
        private final boolean isOnline;

        public InstanceStatus(String systemName, String instanceId, Instant lastHealthcheckTime, boolean isOnline) {
            this.systemName = systemName;
            this.instanceId = instanceId;
            this.lastHealthcheckTime = lastHealthcheckTime;
            this.isOnline = isOnline;
        }

        public String getSystemName() {
            return systemName;
        }

        public String getInstanceId() {
            return instanceId;
        }

        public Instant getLastHealthcheckTime() {
            return lastHealthcheckTime;
        }

        public boolean isOnline() {
            return isOnline;
        }
    }
}

