package com.mockcontroller.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockcontroller.model.ScheduledConfigUpdate;
import com.mockcontroller.model.entity.ScheduledConfigUpdateEntity;
import org.springframework.stereotype.Component;

@Component
public class ScheduledConfigMapper {

    private final ObjectMapper objectMapper;

    public ScheduledConfigMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ScheduledConfigUpdateEntity toEntity(ScheduledConfigUpdate update) {
        if (update == null) {
            return null;
        }
        ScheduledConfigUpdateEntity entity = new ScheduledConfigUpdateEntity();
        entity.setId(update.getId());
        entity.setSystemName(update.getSystemName());
        entity.setNewConfigJson(jsonToString(update.getNewConfig()));
        entity.setScheduledTime(update.getScheduledTime());
        entity.setCreatedAt(update.getCreatedAt());
        entity.setComment(update.getComment());
        return entity;
    }

    public ScheduledConfigUpdate toModel(ScheduledConfigUpdateEntity entity) {
        if (entity == null) {
            return null;
        }
        ScheduledConfigUpdate update = new ScheduledConfigUpdate();
        update.setId(entity.getId());
        update.setSystemName(entity.getSystemName());
        update.setNewConfig(stringToJson(entity.getNewConfigJson()));
        update.setScheduledTime(entity.getScheduledTime());
        update.setCreatedAt(entity.getCreatedAt());
        update.setComment(entity.getComment());
        return update;
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

