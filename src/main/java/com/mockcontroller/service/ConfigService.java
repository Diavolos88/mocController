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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConfigService {

    private final ObjectMapper objectMapper;
    private final Map<String, StoredConfig> configs = new ConcurrentHashMap<>();
    private final Path storageDir = Path.of("data", "configs");

    public ConfigService(ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        initStorage();
        loadExistingConfigs();
    }

    public Collection<StoredConfig> findAll() {
        return configs.values().stream()
                .sorted((a, b) -> a.getSystemName().compareToIgnoreCase(b.getSystemName()))
                .toList();
    }

    public Optional<StoredConfig> findBySystemName(String systemName) {
        return Optional.ofNullable(configs.get(systemName));
    }

    public void updateCurrentConfig(String systemName, JsonNode newConfig) {
        StoredConfig stored = configs.get(systemName);
        if (stored == null) {
            throw new IllegalArgumentException("Config not found: " + systemName);
        }
        // Увеличиваем версию при обновлении через UI
        int newVersion = stored.getVersion() + 1;
        stored.setVersion(newVersion);
        stored.setCurrentConfig(newConfig);
        stored.setUpdatedAt(Instant.now());
        persist(stored);
    }

    public void revertToStart(String systemName) {
        StoredConfig stored = configs.get(systemName);
        if (stored == null) {
            throw new IllegalArgumentException("Config not found: " + systemName);
        }
        // При откате увеличиваем версию
        int newVersion = stored.getVersion() + 1;
        stored.setVersion(newVersion);
        stored.setCurrentConfig(stored.getStartConfig());
        stored.setUpdatedAt(Instant.now());
        persist(stored);
    }

    public CheckUpdateResponse checkUpdate(CheckUpdateRequest request) {
        String sanitizedName = sanitize(request.getSystemName());
        String incomingVersion = request.getVersion();
        JsonNode incomingConfig = request.getConfig();
        StoredConfig current = configs.get(sanitizedName);

        int incomingVersionInt = parseVersion(incomingVersion);

        if (current == null) {
            // Конфига нет - если версия 1, сохраняем как новый стартовый конфиг
            if (incomingVersionInt == 1) {
                StoredConfig stored = new StoredConfig(sanitizedName, incomingConfig, incomingConfig, Instant.now());
                stored.setVersion(1);
                configs.put(sanitizedName, stored);
                persist(stored);
                return new CheckUpdateResponse(false, "v1");
            }
            // Если версия не 1, но конфига нет - нужна регистрация с версией 1
            return new CheckUpdateResponse(false, null);
        }

        // Получаем текущую версию из объекта
        int currentVersion = current.getVersion();

        // Если версия 1, но конфиг отличается от стартового - перезаписываем стартовый
        if (incomingVersionInt == 1) {
            if (!jsonEquals(current.getStartConfig(), incomingConfig)) {
                // Конфиг изменился - обновляем стартовый и повышаем версию
                int newVersion = currentVersion + 1;
                current.setVersion(newVersion);
                current.setStartConfig(incomingConfig);
                current.setCurrentConfig(incomingConfig);
                current.setUpdatedAt(Instant.now());
                persist(current);
                return new CheckUpdateResponse(false, "v" + newVersion);
            }
            // Конфиг совпадает - обновлений не требуется
            return new CheckUpdateResponse(false, "v" + currentVersion);
        }

        // Если версия заглушки меньше нашей (но не 1) - нужна обновление
        if (incomingVersionInt < currentVersion) {
            return new CheckUpdateResponse(true, "v" + currentVersion);
        }

        // Версии совпадают или заглушка новее - обновлений не требуется
        return new CheckUpdateResponse(false, "v" + currentVersion);
    }

    public ConfigSyncResponse handleIncoming(ConfigRequest request) {
        String sanitizedName = sanitize(request.getSystemName());
        StoredConfig current = configs.get(sanitizedName);
        JsonNode incoming = request.getConfig();

        if (current == null) {
            // Первая регистрация - сохраняем как есть
            StoredConfig stored = new StoredConfig(sanitizedName, incoming, incoming, Instant.now());
            // Версия по умолчанию 1
            stored.setVersion(1);
            configs.put(sanitizedName, stored);
            persist(stored);
            return new ConfigSyncResponse(SyncStatus.START_REGISTERED,
                    "Start config saved", "v1");
        }

        // Сравниваем стартовый конфиг
        if (!jsonEquals(current.getStartConfig(), incoming)) {
            // Стартовый конфиг изменился - обновляем и повышаем версию
            int newVersion = current.getVersion() + 1;
            current.setVersion(newVersion);
            current.setStartConfig(incoming);
            current.setCurrentConfig(incoming);
            current.setUpdatedAt(Instant.now());
            persist(current);
            return new ConfigSyncResponse(SyncStatus.UPDATED_START_CONFIG,
                    "Start config updated", "v" + newVersion);
        }

        // Сравниваем текущий конфиг
        if (!jsonEquals(current.getCurrentConfig(), incoming)) {
            return new ConfigSyncResponse(SyncStatus.UPDATE_AVAILABLE,
                    "New config version available", "v" + current.getVersion());
        }

        return new ConfigSyncResponse(SyncStatus.NO_CHANGES,
                "No changes", "v" + current.getVersion());
    }

    public ConfigResponse getConfig(String systemName, String version) {
        String sanitizedName = sanitize(systemName);
        StoredConfig stored = configs.get(sanitizedName);
        
        if (stored == null) {
            throw new IllegalArgumentException("Config not found: " + systemName);
        }
        
        int requestedVersion = parseVersion(version);
        int currentVersion = stored.getVersion();
        
        JsonNode configToReturn;
        String versionToReturn;
        
        // Если версия не указана или указана текущая - возвращаем текущий конфиг
        if (version == null || version.isEmpty() || requestedVersion == currentVersion) {
            configToReturn = stored.getCurrentConfig();
            versionToReturn = "v" + currentVersion;
        } 
        // Если запрошена версия 1 - возвращаем стартовый конфиг
        else if (requestedVersion == 1) {
            configToReturn = stored.getStartConfig();
            versionToReturn = "v1";
        }
        // Если запрошена несуществующая версия - возвращаем текущий конфиг с предупреждением
        else {
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
        // Парсим "v1", "v1.0", "1", "1.0" -> 1
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

    private void initStorage() throws IOException {
        if (!Files.exists(storageDir)) {
            Files.createDirectories(storageDir);
        }
    }

    private void loadExistingConfigs() throws IOException {
        if (!Files.exists(storageDir)) {
            return;
        }
        try (java.util.stream.Stream<Path> files = Files.list(storageDir)) {
            files.filter(path -> path.toString().endsWith(".json"))
                    .forEach(this::loadConfigFromFile);
        }
    }

    private void loadConfigFromFile(Path file) {
        try {
            JsonNode node = objectMapper.readTree(file.toFile());
            StoredConfig stored = new StoredConfig();
            stored.setSystemName(node.get("systemName").asText());
            stored.setStartConfig(node.get("startConfig"));
            stored.setCurrentConfig(node.get("currentConfig"));
            stored.setUpdatedAt(Instant.parse(node.get("updatedAt").asText()));
            // Загружаем версию, если есть, иначе ставим 1
            if (node.has("version")) {
                stored.setVersion(node.get("version").asInt());
            } else {
                stored.setVersion(1);
            }
            configs.put(stored.getSystemName(), stored);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read config " + file, e);
        }
    }

    private void persist(StoredConfig stored) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("systemName", stored.getSystemName());
            node.set("startConfig", stored.getStartConfig());
            node.set("currentConfig", stored.getCurrentConfig());
            node.put("updatedAt", stored.getUpdatedAt().toString());
            node.put("version", stored.getVersion());
            Files.writeString(filePath(stored.getSystemName()),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config " + stored.getSystemName(), e);
        }
    }

    private Path filePath(String name) {
        return storageDir.resolve(sanitize(name) + ".json");
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9-_]", "_");
    }

    private boolean jsonEquals(JsonNode left, JsonNode right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
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
        
        // Используем версию из объекта
        dto.setConfigVersion("v" + stored.getVersion());
        
        // РџР°СЂСЃРёРј delays (int Р·РЅР°С‡РµРЅРёСЏ)
        JsonNode currentDelays = current.get("delays");
        JsonNode startDelays = start != null ? start.get("delays") : null;
        if (currentDelays != null && currentDelays.isObject()) {
            currentDelays.fields().forEachRemaining(entry -> {
                ConfigParamDto param = new ConfigParamDto();
                param.setKey(entry.getKey());
                param.setValue(entry.getValue().asText());
                param.setType("int");
                
                // РќР°С…РѕРґРёРј СЃС‚Р°СЂС‚РѕРІРѕРµ Р·РЅР°С‡РµРЅРёРµ
                if (startDelays != null && startDelays.has(entry.getKey())) {
                    param.setStartValue(startDelays.get(entry.getKey()).asText());
                } else {
                    param.setStartValue(entry.getValue().asText());
                }
                
                dto.getDelays().add(param);
            });
        }
        
        // РџР°СЂСЃРёРј stringParams (СЃС‚СЂРѕРєРѕРІС‹Рµ Р·РЅР°С‡РµРЅРёСЏ)
        JsonNode currentStringParams = current.get("stringParams");
        JsonNode startStringParams = start != null ? start.get("stringParams") : null;
        if (currentStringParams != null && currentStringParams.isObject()) {
            currentStringParams.fields().forEachRemaining(entry -> {
                ConfigParamDto param = new ConfigParamDto();
                param.setKey(entry.getKey());
                param.setValue(entry.getValue().asText());
                param.setType("string");
                
                // РќР°С…РѕРґРёРј СЃС‚Р°СЂС‚РѕРІРѕРµ Р·РЅР°С‡РµРЅРёРµ
                if (startStringParams != null && startStringParams.has(entry.getKey())) {
                    param.setStartValue(startStringParams.get(entry.getKey()).asText());
                } else {
                    param.setStartValue(entry.getValue().asText());
                }
                
                dto.getStringParams().add(param);
            });
        }
        
        // РџР°СЂСЃРёРј loggingLv (СѓСЂРѕРІРµРЅСЊ Р»РѕРіРёСЂРѕРІР°РЅРёСЏ)
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

    public void updateConfigFromForm(String systemName, Map<String, String> delays, 
                                     Map<String, String> stringParams, String loggingLv) {
        StoredConfig stored = configs.get(systemName);
        if (stored == null) {
            throw new IllegalArgumentException("Config not found: " + systemName);
        }
        
        // Увеличиваем версию
        stored.setVersion(stored.getVersion() + 1);
        
        ObjectNode newConfig = objectMapper.createObjectNode();
        
        // Обновляем delays
        ObjectNode delaysNode = objectMapper.createObjectNode();
        if (delays != null) {
            delays.forEach((key, value) -> {
                if (value != null && !value.isEmpty()) {
                    try {
                        delaysNode.put(key, Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        delaysNode.put(key, value); // fallback
                    }
                }
            });
        }
        newConfig.set("delays", delaysNode);
        
        // РћР±РЅРѕРІР»СЏРµРј stringParams
        ObjectNode stringParamsNode = objectMapper.createObjectNode();
        if (stringParams != null) {
            stringParams.forEach((key, value) -> {
                if (value != null) {
                    stringParamsNode.put(key, value);
                }
            });
        }
        newConfig.set("stringParams", stringParamsNode);
        
        // Обновляем loggingLv
        if (loggingLv != null && !loggingLv.isEmpty()) {
            newConfig.put("loggingLv", loggingLv);
        }
        
        stored.setCurrentConfig(newConfig);
        stored.setUpdatedAt(Instant.now());
        persist(stored);
    }

    public JsonNode createConfigFromForm(Map<String, String> delays, Map<String, String> stringParams, String loggingLv) {
        ObjectNode newConfig = objectMapper.createObjectNode();
        
        // Создаем delays
        ObjectNode delaysNode = objectMapper.createObjectNode();
        if (delays != null) {
            delays.forEach((key, value) -> {
                if (value != null && !value.isEmpty()) {
                    try {
                        delaysNode.put(key, Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        delaysNode.put(key, value); // fallback
                    }
                }
            });
        }
        newConfig.set("delays", delaysNode);
        
        // Создаем stringParams
        ObjectNode stringParamsNode = objectMapper.createObjectNode();
        if (stringParams != null) {
            stringParams.forEach((key, value) -> {
                if (value != null) {
                    stringParamsNode.put(key, value);
                }
            });
        }
        newConfig.set("stringParams", stringParamsNode);
        
        // Добавляем loggingLv
        if (loggingLv != null && !loggingLv.isEmpty()) {
            newConfig.put("loggingLv", loggingLv);
        }
        
        return newConfig;
    }
}
