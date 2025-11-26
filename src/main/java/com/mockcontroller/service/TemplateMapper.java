package com.mockcontroller.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockcontroller.model.Template;
import com.mockcontroller.model.entity.TemplateEntity;
import org.springframework.stereotype.Component;

@Component
public class TemplateMapper {

    private final ObjectMapper objectMapper;

    public TemplateMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Template toModel(TemplateEntity entity) {
        if (entity == null) {
            return null;
        }
        
        try {
            JsonNode config = objectMapper.readTree(entity.getConfigJson());
            return new Template(
                entity.getId(),
                entity.getName(),
                entity.getSystemName(),
                config,
                entity.getDescription(),
                entity.getCreatedAt()
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse template config JSON", e);
        }
    }

    public TemplateEntity toEntity(Template template) {
        if (template == null) {
            return null;
        }
        
        TemplateEntity entity = new TemplateEntity();
        // Устанавливаем ID только если он уже есть (для существующих шаблонов)
        // Для новых шаблонов ID будет сгенерирован в конструкторе TemplateEntity
        if (template.getId() != null && !template.getId().isEmpty()) {
            entity.setId(template.getId());
        }
        entity.setName(template.getName());
        entity.setSystemName(template.getSystemName());
        entity.setDescription(template.getDescription());
        // Устанавливаем createdAt только если он уже есть, иначе будет установлен в конструкторе
        if (template.getCreatedAt() != null) {
            entity.setCreatedAt(template.getCreatedAt());
        }
        
        try {
            String configJson = objectMapper.writeValueAsString(template.getConfig());
            entity.setConfigJson(configJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize template config to JSON", e);
        }
        
        return entity;
    }
}

