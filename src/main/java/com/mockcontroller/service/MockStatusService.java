package com.mockcontroller.service;

import com.mockcontroller.model.entity.MockInstanceEntity;
import com.mockcontroller.repository.MockInstanceRepository;
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

    private static final int OFFLINE_THRESHOLD_MINUTES = 5;

    private final MockInstanceRepository repository;

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
        List<MockInstanceEntity> allInstances = repository.findAllByOrderBySystemNameAsc();
        
        Map<String, SystemStatus> statusMap = new LinkedHashMap<>();
        
        for (MockInstanceEntity instance : allInstances) {
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
        Instant threshold = Instant.now().minus(OFFLINE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
        return instance.getLastHealthcheckTime().isAfter(threshold);
    }

    @Scheduled(fixedRate = 60000) // Каждую минуту
    @Transactional
    public void cleanupOldInstances() {
        // Удаляем инстансы, которые не отправляли healthcheck более 1 часа
        Instant threshold = Instant.now().minus(1, ChronoUnit.HOURS);
        repository.deleteOldInstances(threshold);
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

