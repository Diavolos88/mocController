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
    private final GroupService groupService;

    public ConfigService(ObjectMapper objectMapper, StoredConfigRepository repository, 
                        ConfigMapper mapper, GroupService groupService) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.mapper = mapper;
        this.groupService = groupService;
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
        // Валидируем конфиг перед обновлением
        validateConfig(newConfig);
        
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
        
        // Валидируем входящий конфиг
        if (incomingConfig != null) {
            validateConfig(incomingConfig);
        }
        
        Optional<StoredConfigEntity> currentOpt = repository.findBySystemName(sanitizedName);

        int incomingVersionInt = parseVersion(incomingVersion);

        if (currentOpt.isEmpty()) {
            // Конфига нет - сохраняем как новый стартовый конфиг
            // Если версия выше 1, используем её, иначе ставим 1
            int versionToUse = incomingVersionInt >= 1 ? incomingVersionInt : 1;
            StoredConfigEntity entity = new StoredConfigEntity();
            entity.setSystemName(sanitizedName);
            entity.setStartConfigJson(jsonToString(incomingConfig));
            entity.setCurrentConfigJson(jsonToString(incomingConfig));
            entity.setUpdatedAt(Instant.now());
            entity.setVersion(versionToUse);
            repository.save(entity);
            
            // Автоматическое создание/обновление группы для моков с шаблоном system-integration-mock
            autoCreateOrUpdateGroup(request.getSystemName());
            
            return new CheckUpdateResponse(false, "v" + versionToUse);
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
        // Валидируем конфиг перед обработкой
        if (request.getConfig() != null) {
            validateConfig(request.getConfig());
        }
        String sanitizedName = sanitize(request.getSystemName());
        Optional<StoredConfigEntity> currentOpt = repository.findBySystemName(sanitizedName);
        JsonNode incoming = request.getConfig();

        if (currentOpt.isEmpty()) {
            // Первая регистрация или регистрация после удаления
            // Если в запросе есть версия и она выше 1, используем её, иначе ставим 1
            String requestVersion = request.getVersion();
            int versionToUse = 1;
            if (requestVersion != null && !requestVersion.trim().isEmpty()) {
                int parsedVersion = parseVersion(requestVersion);
                if (parsedVersion > 1) {
                    versionToUse = parsedVersion;
                }
            }
            
            StoredConfigEntity entity = new StoredConfigEntity();
            entity.setSystemName(sanitizedName);
            entity.setStartConfigJson(jsonToString(incoming));
            entity.setCurrentConfigJson(jsonToString(incoming));
            entity.setUpdatedAt(Instant.now());
            entity.setVersion(versionToUse);
            repository.save(entity);
            
            // Автоматическое создание/обновление группы для моков с шаблоном system-integration-mock
            autoCreateOrUpdateGroup(request.getSystemName());
            
            return new ConfigSyncResponse(SyncStatus.START_REGISTERED,
                    "Start config saved", "v" + versionToUse);
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

    /**
     * Автоматически создает или обновляет группу для моков, следующих шаблону system-integration-mock.
     * Если мок соответствует шаблону, извлекается название системы и:
     * - Если группы с таким названием нет - создается группа и добавляется мок
     * - Если группа существует, но мока в ней нет - мок добавляется в группу
     */
    @Transactional
    private void autoCreateOrUpdateGroup(String systemName) {
        if (systemName == null || systemName.isEmpty()) {
            return;
        }
        
        // Проверяем, соответствует ли мок шаблону system-integration-mock
        if (!isValidTemplate(systemName)) {
            return;
        }
        
        // Извлекаем название системы (до первого тире)
        String groupName = extractSystemPrefix(systemName);
        if (groupName == null || groupName.isEmpty()) {
            return;
        }
        
        // Ищем группу с таким названием (без учета регистра)
        Optional<com.mockcontroller.model.Group> groupOpt = groupService.findByName(groupName);
        
        if (groupOpt.isEmpty()) {
            // Группы нет - создаем новую группу с этим моком
            groupService.createGroup(
                groupName,
                "Автоматически созданная группа для системы " + groupName,
                java.util.Collections.singletonList(systemName)
            );
        } else {
            // Группа существует - проверяем, есть ли в ней этот мок
            com.mockcontroller.model.Group group = groupOpt.get();
            if (!group.getSystemNames().contains(systemName)) {
                // Мока нет в группе - добавляем его
                java.util.List<String> updatedSystems = new java.util.ArrayList<>(group.getSystemNames());
                updatedSystems.add(systemName);
                groupService.updateGroup(
                    group.getId(),
                    group.getName(),
                    group.getDescription(),
                    updatedSystems
                );
            }
        }
    }
    
    /**
     * Проверяет, соответствует ли название мока шаблону system-integration-mock.
     * Шаблон: минимум 2 тире и заканчивается на -mock
     */
    private boolean isValidTemplate(String systemName) {
        if (systemName == null || systemName.isEmpty()) {
            return false;
        }
        // Шаблон: название_системы-название_эмуляции-mock
        // Должно быть минимум 2 тире и заканчиваться на -mock
        int dashCount = 0;
        for (char c : systemName.toCharArray()) {
            if (c == '-') {
                dashCount++;
            }
        }
        return dashCount >= 2 && systemName.endsWith("-mock");
    }
    
    /**
     * Извлекает название системы из шаблона system-integration-mock.
     * Возвращает часть до первого тире.
     */
    private String extractSystemPrefix(String systemName) {
        if (systemName == null || systemName.isEmpty()) {
            return systemName;
        }
        // Берем первое слово до первого тире
        int firstDashIndex = systemName.indexOf('-');
        if (firstDashIndex > 0) {
            return systemName.substring(0, firstDashIndex);
        }
        return systemName;
    }
    
    private String sanitize(String name) {
        if (name == null) {
            return "";
        }
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

    /**
     * Нормализует JsonNode для корректного сравнения
     */
    private JsonNode normalizeJsonNode(JsonNode node) {
        if (node == null) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(objectMapper.writeValueAsString(node));
        } catch (Exception e) {
            return node;
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
            JsonNode normalizedLeft = normalizeJsonNode(left);
            JsonNode normalizedRight = normalizeJsonNode(right);
            String leftStr = objectMapper.writeValueAsString(normalizedLeft);
            String rightStr = objectMapper.writeValueAsString(normalizedRight);
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
        
        JsonNode currentIntParams = current.get("intParams");
        JsonNode startIntParams = start != null ? start.get("intParams") : null;
        if (currentIntParams != null && currentIntParams.isObject()) {
            currentIntParams.fields().forEachRemaining(entry -> {
                ConfigParamDto param = new ConfigParamDto();
                param.setKey(entry.getKey());
                param.setValue(entry.getValue().asText());
                param.setType("int");
                
                if (startIntParams != null && startIntParams.has(entry.getKey())) {
                    param.setStartValue(startIntParams.get(entry.getKey()).asText());
                } else {
                    param.setStartValue(entry.getValue().asText());
                }
                
                dto.getIntParams().add(param);
            });
        }
        
        JsonNode currentBooleanVariables = current.get("booleanVariables");
        JsonNode startBooleanVariables = start != null ? start.get("booleanVariables") : null;
        if (currentBooleanVariables != null && currentBooleanVariables.isObject()) {
            currentBooleanVariables.fields().forEachRemaining(entry -> {
                ConfigParamDto param = new ConfigParamDto();
                param.setKey(entry.getKey());
                param.setValue(entry.getValue().asText());
                param.setType("boolean");
                
                if (startBooleanVariables != null && startBooleanVariables.has(entry.getKey())) {
                    param.setStartValue(startBooleanVariables.get(entry.getKey()).asText());
                } else {
                    param.setStartValue(entry.getValue().asText());
                }
                
                dto.getBooleanVariables().add(param);
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
                                        Map<String, String> stringParams, Map<String, String> intParams, Map<String, String> booleanVariables, String loggingLv) {
        StoredConfigEntity entity = repository.findBySystemName(sanitize(systemName))
                .orElseThrow(() -> new IllegalArgumentException("Config not found: " + systemName));
        
        StoredConfig stored = mapper.toModel(entity);
        
        JsonNode newConfig = createConfigFromForm(delays, stringParams, intParams, booleanVariables, loggingLv);
        
        // Валидируем созданный конфиг
        validateConfig(newConfig);
        
        JsonNode normalizedNewConfig = normalizeJsonNode(newConfig);
        JsonNode normalizedCurrentConfig = normalizeJsonNode(stored.getCurrentConfig());
        
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

    /**
     * Валидирует конфигурацию: delays должны быть числовыми и неотрицательными, intParams - целыми числами
     */
    public void validateConfig(JsonNode config) {
        if (config == null) {
            return;
        }
        
        if (config.has("delays") && config.get("delays").isObject()) {
            config.get("delays").fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();
                
                int intValue;
                if (valueNode.isNumber()) {
                    if (!valueNode.isInt()) {
                        throw new IllegalArgumentException("Значение задержки '" + key + "' должно быть целым числом");
                    }
                    intValue = valueNode.asInt();
                } else if (valueNode.isTextual()) {
                    try {
                        intValue = Integer.parseInt(valueNode.asText());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Значение задержки '" + key + "' должно быть целым числом");
                    }
                } else {
                    throw new IllegalArgumentException("Значение задержки '" + key + "' должно быть целым числом");
                }
                
                if (intValue < 0) {
                    throw new IllegalArgumentException("Значение задержки '" + key + "' должно быть неотрицательным числом");
                }
            });
        }
        
        if (config.has("intParams") && config.get("intParams").isObject()) {
            config.get("intParams").fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();
                
                if (valueNode.isNumber()) {
                    if (!valueNode.isInt()) {
                        throw new IllegalArgumentException("Значение целочисленного параметра '" + key + "' должно быть целым числом");
                    }
                } else if (valueNode.isTextual()) {
                    try {
                        Integer.parseInt(valueNode.asText());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Значение целочисленного параметра '" + key + "' должно быть целым числом");
                    }
                } else {
                    throw new IllegalArgumentException("Значение целочисленного параметра '" + key + "' должно быть целым числом");
                }
            });
        }
        
        if (config.has("booleanVariables") && config.get("booleanVariables").isObject()) {
            config.get("booleanVariables").fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();
                
                if (valueNode.isBoolean()) {
                    // Значение уже булево - все хорошо
                } else if (valueNode.isTextual()) {
                    String textValue = valueNode.asText().toLowerCase().trim();
                    if (!"true".equals(textValue) && !"false".equals(textValue)) {
                        throw new IllegalArgumentException("Значение булевой переменной '" + key + "' должно быть 'true' или 'false', получено: " + valueNode.asText());
                    }
                } else {
                    throw new IllegalArgumentException("Значение булевой переменной '" + key + "' должно быть булевым значением или строкой 'true'/'false'");
                }
            });
        }
        
        // stringParams не требуют специфической валидации, так как могут быть любыми строками
        // loggingLv валидируется на уровне UI и при парсинге в enum
    }

    public JsonNode createConfigFromForm(Map<String, String> delays, Map<String, String> stringParams, Map<String, String> intParams, Map<String, String> booleanVariables, String loggingLv) {
        ObjectNode newConfig = objectMapper.createObjectNode();
        
        newConfig.set("delays", createDelaysNode(delays));
        newConfig.set("stringParams", createStringParamsNode(stringParams));
        newConfig.set("intParams", createIntParamsNode(intParams));
        newConfig.set("booleanVariables", createBooleanVariablesNode(booleanVariables));
        
        if (loggingLv != null && !loggingLv.isEmpty()) {
            newConfig.put("loggingLv", loggingLv);
        }
        
        return newConfig;
    }
    
    /**
     * Создает узел delays из Map
     */
    private ObjectNode createDelaysNode(Map<String, String> delays) {
        ObjectNode delaysNode = objectMapper.createObjectNode();
        if (delays != null) {
            delays.forEach((key, value) -> {
                if (value != null && !value.isEmpty()) {
                    try {
                        int intValue = Integer.parseInt(value);
                        if (intValue < 0) {
                            throw new IllegalArgumentException("Значение задержки '" + key + "' должно быть неотрицательным числом");
                        }
                        delaysNode.put(key, intValue);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Значение задержки '" + key + "' должно быть целым числом, получено: " + value);
                    }
                }
            });
        }
        return delaysNode;
    }
    
    /**
     * Создает узел stringParams из Map
     */
    private ObjectNode createStringParamsNode(Map<String, String> stringParams) {
        ObjectNode stringParamsNode = objectMapper.createObjectNode();
        if (stringParams != null) {
            stringParams.forEach((key, value) -> {
                if (value != null) {
                    stringParamsNode.put(key, value);
                }
            });
        }
        return stringParamsNode;
    }
    
    /**
     * Создает узел intParams из Map
     */
    private ObjectNode createIntParamsNode(Map<String, String> intParams) {
        ObjectNode intParamsNode = objectMapper.createObjectNode();
        if (intParams != null) {
            intParams.forEach((key, value) -> {
                if (value != null && !value.isEmpty()) {
                    try {
                        int intValue = Integer.parseInt(value);
                        intParamsNode.put(key, intValue);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Значение целочисленного параметра '" + key + "' должно быть целым числом, получено: " + value);
                    }
                }
            });
        }
        return intParamsNode;
    }
    
    /**
     * Создает узел booleanVariables из Map
     */
    private ObjectNode createBooleanVariablesNode(Map<String, String> booleanVariables) {
        ObjectNode booleanVariablesNode = objectMapper.createObjectNode();
        if (booleanVariables != null) {
            booleanVariables.forEach((key, value) -> {
                if (value != null && !value.isEmpty()) {
                    String lowerValue = value.toLowerCase().trim();
                    if ("true".equals(lowerValue) || "false".equals(lowerValue)) {
                        booleanVariablesNode.put(key, Boolean.parseBoolean(lowerValue));
                    } else {
                        throw new IllegalArgumentException("Значение булевой переменной '" + key + "' должно быть 'true' или 'false', получено: " + value);
                    }
                }
            });
        }
        return booleanVariablesNode;
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
