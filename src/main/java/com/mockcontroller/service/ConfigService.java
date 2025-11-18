package com.mockcontroller.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mockcontroller.dto.ConfigParamDto;
import com.mockcontroller.dto.ConfigViewDto;
import com.mockcontroller.model.CheckUpdateRequest;
import com.mockcontroller.model.CheckUpdateResponse;
import com.mockcontroller.model.ConfigRequest;
import com.mockcontroller.model.ConfigResponse;
import com.mockcontroller.model.ConfigSyncResponse;
import com.mockcontroller.model.ConfigSyncResponse.SyncStatus;
import com.mockcontroller.model.StoredConfig;
import com.mockcontroller.model.entity.StoredConfigEntity;
import com.mockcontroller.repository.StoredConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ConfigService {

    private final ObjectMapper objectMapper;
    private final StoredConfigRepository repository;
    private final ConfigMapper mapper;

    public ConfigService(ObjectMapper objectMapper, StoredConfigRepository repository, ConfigMapper mapper) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.mapper = mapper;
    }

    public Collection<StoredConfig> findAll() {
        return repository.findAll().stream()
                .map(mapper::toModel)
                .sorted((a, b) -> a.getSystemName().compareToIgnoreCase(b.getSystemName()))
                .collect(Collectors.toList());
    }

    public Optional<StoredConfig> findBySystemName(String systemName) {
        return repository.findBySystemName(sanitize(systemName))
                .map(mapper::toModel);
    }

    @Transactional
    public void updateCurrentConfig(String systemName, JsonNode newConfig) {
        StoredConfigEntity entity = repository.findBySystemName(sanitize(systemName))
                .orElseThrow(() -> new IllegalArgumentException("Config not found: " + systemName));
        
        int newVersion = entity.getVersion() + 1;
        entity.setVersion(newVersion);
        entity.setCurrentConfigJson(jsonToString(newConfig));
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    @Transactional
    public boolean revertToStart(String systemName) {
        StoredConfigEntity entity = repository.findBySystemName(sanitize(systemName))
                .orElseThrow(() -> new IllegalArgumentException("Config not found: " + systemName));
        
        StoredConfig stored = mapper.toModel(entity);
        
        // Проверяем, есть ли изменения
        if (jsonEquals(stored.getCurrentConfig(), stored.getStartConfig())) {
            return false;
        }
        
        // Есть изменения - возвращаем к стартовому и увеличиваем версию
        try {
            JsonNode startConfigCopy = objectMapper.readTree(
                objectMapper.writeValueAsString(stored.getStartConfig())
            );
            int newVersion = entity.getVersion() + 1;
            entity.setVersion(newVersion);
            entity.setCurrentConfigJson(jsonToString(startConfigCopy));
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
            return true;
        } catch (Exception e) {
            int newVersion = entity.getVersion() + 1;
            entity.setVersion(newVersion);
            entity.setCurrentConfigJson(entity.getStartConfigJson());
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
            return true;
        }
    }

    @Transactional
    public CheckUpdateResponse checkUpdate(CheckUpdateRequest request) {
        String sanitizedName = sanitize(request.getSystemName());
        String incomingVersion = request.getVersion();
        JsonNode incomingConfig = request.getConfig();
        Optional<StoredConfigEntity> currentOpt = repository.findBySystemName(sanitizedName);

        int incomingVersionInt = parseVersion(incomingVersion);

        if (currentOpt.isEmpty()) {
            // Конфига нет - если версия 1, сохраняем как новый стартовый конфиг
            if (incomingVersionInt == 1) {
                StoredConfigEntity entity = new StoredConfigEntity();
                entity.setSystemName(sanitizedName);
                entity.setStartConfigJson(jsonToString(incomingConfig));
                entity.setCurrentConfigJson(jsonToString(incomingConfig));
                entity.setUpdatedAt(Instant.now());
                entity.setVersion(1);
                repository.save(entity);
                return new CheckUpdateResponse(false, "v1");
            }
            return new CheckUpdateResponse(false, null);
        }

        StoredConfigEntity current = currentOpt.get();
        int currentVersion = current.getVersion();

        // Если версия 1, но конфиг отличается от стартового - перезаписываем стартовый
        if (incomingVersionInt == 1) {
            StoredConfig stored = mapper.toModel(current);
            if (!jsonEquals(stored.getStartConfig(), incomingConfig)) {
                int newVersion = currentVersion + 1;
                current.setVersion(newVersion);
                current.setStartConfigJson(jsonToString(incomingConfig));
                current.setCurrentConfigJson(jsonToString(incomingConfig));
                current.setUpdatedAt(Instant.now());
                repository.save(current);
                return new CheckUpdateResponse(false, "v" + newVersion);
            }
            return new CheckUpdateResponse(false, "v" + currentVersion);
        }

        // Если версия заглушки меньше нашей - нужна обновление
        if (incomingVersionInt < currentVersion) {
            return new CheckUpdateResponse(true, "v" + currentVersion);
        }

        return new CheckUpdateResponse(false, "v" + currentVersion);
    }

    @Transactional
    public ConfigSyncResponse handleIncoming(ConfigRequest request) {
        String sanitizedName = sanitize(request.getSystemName());
        Optional<StoredConfigEntity> currentOpt = repository.findBySystemName(sanitizedName);
        JsonNode incoming = request.getConfig();

        if (currentOpt.isEmpty()) {
            // Первая регистрация
            StoredConfigEntity entity = new StoredConfigEntity();
            entity.setSystemName(sanitizedName);
            entity.setStartConfigJson(jsonToString(incoming));
            entity.setCurrentConfigJson(jsonToString(incoming));
            entity.setUpdatedAt(Instant.now());
            entity.setVersion(1);
            repository.save(entity);
            return new ConfigSyncResponse(SyncStatus.START_REGISTERED,
                    "Start config saved", "v1");
        }

        StoredConfigEntity current = currentOpt.get();
        StoredConfig stored = mapper.toModel(current);

        // Сравниваем стартовый конфиг
        if (!jsonEquals(stored.getStartConfig(), incoming)) {
            int newVersion = current.getVersion() + 1;
            current.setVersion(newVersion);
            current.setStartConfigJson(jsonToString(incoming));
            current.setCurrentConfigJson(jsonToString(incoming));
            current.setUpdatedAt(Instant.now());
            repository.save(current);
            return new ConfigSyncResponse(SyncStatus.UPDATED_START_CONFIG,
                    "Start config updated", "v" + newVersion);
        }

        // Сравниваем текущий конфиг
        if (!jsonEquals(stored.getCurrentConfig(), incoming)) {
            return new ConfigSyncResponse(SyncStatus.UPDATE_AVAILABLE,
                    "New config version available", "v" + current.getVersion());
        }

        return new ConfigSyncResponse(SyncStatus.NO_CHANGES,
                "No changes", "v" + current.getVersion());
    }

    public ConfigResponse getConfig(String systemName, String version) {
        StoredConfigEntity entity = repository.findBySystemName(sanitize(systemName))
                .orElseThrow(() -> new IllegalArgumentException("Config not found: " + systemName));
        
        StoredConfig stored = mapper.toModel(entity);
        int requestedVersion = parseVersion(version);
        int currentVersion = stored.getVersion();
        
        JsonNode configToReturn;
        String versionToReturn;
        
        if (version == null || version.isEmpty() || requestedVersion == currentVersion) {
            configToReturn = stored.getCurrentConfig();
            versionToReturn = "v" + currentVersion;
        } else if (requestedVersion == 1) {
            configToReturn = stored.getStartConfig();
            versionToReturn = "v1";
        } else {
            configToReturn = stored.getCurrentConfig();
            versionToReturn = "v" + currentVersion;
        }
        
        return new ConfigResponse(
            stored.getSystemName(),
            versionToReturn,
            configToReturn,
            stored.getUpdatedAt().toString()
        );
    }

    private int parseVersion(String versionStr) {
        if (versionStr == null || versionStr.isEmpty()) {
            return 1;
        }
        String cleaned = versionStr.trim();
        if (cleaned.startsWith("v")) {
            cleaned = cleaned.substring(1);
        }
        int dotIndex = cleaned.indexOf('.');
        if (dotIndex > 0) {
            cleaned = cleaned.substring(0, dotIndex);
        }
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9-_]", "_");
    }

    private String jsonToString(JsonNode jsonNode) {
        if (jsonNode == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            return jsonNode.toString();
        }
    }

    private boolean jsonEquals(JsonNode left, JsonNode right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        try {
            String leftStr = objectMapper.writeValueAsString(
                objectMapper.readTree(objectMapper.writeValueAsString(left))
            );
            String rightStr = objectMapper.writeValueAsString(
                objectMapper.readTree(objectMapper.writeValueAsString(right))
            );
            return leftStr.equals(rightStr);
        } catch (Exception e) {
            return left.equals(right);
        }
    }

    public String toPrettyJson(JsonNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return node != null ? node.toString() : "{}";
        }
    }

    public JsonNode parseJson(String json) throws JsonProcessingException {
        return objectMapper.readTree(json);
    }

    public ConfigViewDto toConfigViewDto(StoredConfig stored) {
        ConfigViewDto dto = new ConfigViewDto();
        dto.setSystemName(stored.getSystemName());
        
        JsonNode current = stored.getCurrentConfig();
        JsonNode start = stored.getStartConfig();
        
        dto.setConfigVersion("v" + stored.getVersion());
        
        JsonNode currentDelays = current.get("delays");
        JsonNode startDelays = start != null ? start.get("delays") : null;
        if (currentDelays != null && currentDelays.isObject()) {
            currentDelays.fields().forEachRemaining(entry -> {
                ConfigParamDto param = new ConfigParamDto();
                param.setKey(entry.getKey());
                param.setValue(entry.getValue().asText());
                param.setType("int");
                
                if (startDelays != null && startDelays.has(entry.getKey())) {
                    param.setStartValue(startDelays.get(entry.getKey()).asText());
                } else {
                    param.setStartValue(entry.getValue().asText());
                }
                
                dto.getDelays().add(param);
            });
        }
        
        JsonNode currentStringParams = current.get("stringParams");
        JsonNode startStringParams = start != null ? start.get("stringParams") : null;
        if (currentStringParams != null && currentStringParams.isObject()) {
            currentStringParams.fields().forEachRemaining(entry -> {
                ConfigParamDto param = new ConfigParamDto();
                param.setKey(entry.getKey());
                param.setValue(entry.getValue().asText());
                param.setType("string");
                
                if (startStringParams != null && startStringParams.has(entry.getKey())) {
                    param.setStartValue(startStringParams.get(entry.getKey()).asText());
                } else {
                    param.setStartValue(entry.getValue().asText());
                }
                
                dto.getStringParams().add(param);
            });
        }
        
        JsonNode currentLogging = current.get("loggingLv");
        JsonNode startLogging = start != null ? start.get("loggingLv") : null;
        if (currentLogging != null) {
            ConfigParamDto loggingParam = new ConfigParamDto();
            loggingParam.setKey("loggingLv");
            loggingParam.setValue(currentLogging.asText());
            loggingParam.setType("logLevel");
            
            if (startLogging != null) {
                loggingParam.setStartValue(startLogging.asText());
            } else {
                loggingParam.setStartValue(currentLogging.asText());
            }
            
            dto.setLoggingLevel(loggingParam);
        }
        
        return dto;
    }

    @Transactional
    public boolean updateConfigFromForm(String systemName, Map<String, String> delays, 
                                        Map<String, String> stringParams, String loggingLv) {
        StoredConfigEntity entity = repository.findBySystemName(sanitize(systemName))
                .orElseThrow(() -> new IllegalArgumentException("Config not found: " + systemName));
        
        StoredConfig stored = mapper.toModel(entity);
        
        ObjectNode newConfig = objectMapper.createObjectNode();
        
        ObjectNode delaysNode = objectMapper.createObjectNode();
        if (delays != null) {
            delays.forEach((key, value) -> {
                if (value != null && !value.isEmpty()) {
                    try {
                        delaysNode.put(key, Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        delaysNode.put(key, value);
                    }
                }
            });
        }
        newConfig.set("delays", delaysNode);
        
        ObjectNode stringParamsNode = objectMapper.createObjectNode();
        if (stringParams != null) {
            stringParams.forEach((key, value) -> {
                if (value != null) {
                    stringParamsNode.put(key, value);
                }
            });
        }
        newConfig.set("stringParams", stringParamsNode);
        
        if (loggingLv != null && !loggingLv.isEmpty()) {
            newConfig.put("loggingLv", loggingLv);
        }
        
        JsonNode normalizedNewConfig;
        try {
            normalizedNewConfig = objectMapper.readTree(objectMapper.writeValueAsString(newConfig));
        } catch (Exception e) {
            normalizedNewConfig = newConfig;
        }
        
        JsonNode normalizedCurrentConfig;
        try {
            normalizedCurrentConfig = objectMapper.readTree(objectMapper.writeValueAsString(stored.getCurrentConfig()));
        } catch (Exception e) {
            normalizedCurrentConfig = stored.getCurrentConfig();
        }
        
        boolean hasChanges = !jsonEquals(normalizedCurrentConfig, normalizedNewConfig);
        
        if (!hasChanges) {
            return false;
        }
        
        entity.setVersion(entity.getVersion() + 1);
        entity.setCurrentConfigJson(jsonToString(normalizedNewConfig));
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
        return true;
    }

    public JsonNode createConfigFromForm(Map<String, String> delays, Map<String, String> stringParams, String loggingLv) {
        ObjectNode newConfig = objectMapper.createObjectNode();
        
        ObjectNode delaysNode = objectMapper.createObjectNode();
        if (delays != null) {
            delays.forEach((key, value) -> {
                if (value != null && !value.isEmpty()) {
                    try {
                        delaysNode.put(key, Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        delaysNode.put(key, value);
                    }
                }
            });
        }
        newConfig.set("delays", delaysNode);
        
        ObjectNode stringParamsNode = objectMapper.createObjectNode();
        if (stringParams != null) {
            stringParams.forEach((key, value) -> {
                if (value != null) {
                    stringParamsNode.put(key, value);
                }
            });
        }
        newConfig.set("stringParams", stringParamsNode);
        
        if (loggingLv != null && !loggingLv.isEmpty()) {
            newConfig.put("loggingLv", loggingLv);
        }
        
        return newConfig;
    }

    @Transactional
    public void deleteConfig(String systemName) {
        String sanitizedName = sanitize(systemName);
        if (!repository.existsBySystemName(sanitizedName)) {
            throw new IllegalArgumentException("Config not found: " + systemName);
        }
        repository.deleteById(sanitizedName); // sanitizedName не может быть null после sanitize()
    }
}
