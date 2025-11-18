package com.mockcontroller.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockcontroller.model.StoredConfig;
import com.mockcontroller.model.entity.StoredConfigEntity;
import org.springframework.stereotype.Component;

@Component
public class ConfigMapper {

    private final ObjectMapper objectMapper;

    public ConfigMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StoredConfigEntity toEntity(StoredConfig stored) {
        if (stored == null) {
            return null;
        }
        StoredConfigEntity entity = new StoredConfigEntity();
        entity.setSystemName(stored.getSystemName());
        entity.setStartConfigJson(jsonToString(stored.getStartConfig()));
        entity.setCurrentConfigJson(jsonToString(stored.getCurrentConfig()));
        entity.setUpdatedAt(stored.getUpdatedAt());
        entity.setVersion(stored.getVersion());
        return entity;
    }

    public StoredConfig toModel(StoredConfigEntity entity) {
        if (entity == null) {
            return null;
        }
        StoredConfig stored = new StoredConfig();
        stored.setSystemName(entity.getSystemName());
        stored.setStartConfig(stringToJson(entity.getStartConfigJson()));
        stored.setCurrentConfig(stringToJson(entity.getCurrentConfigJson()));
        stored.setUpdatedAt(entity.getUpdatedAt());
        stored.setVersion(entity.getVersion());
        return stored;
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

    private JsonNode stringToJson(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            try {
                return objectMapper.readTree("{}");
            } catch (JsonProcessingException e) {
                return objectMapper.createObjectNode();
            }
        }
        try {
            return objectMapper.readTree(jsonString);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }
}

