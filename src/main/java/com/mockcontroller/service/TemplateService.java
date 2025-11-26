package com.mockcontroller.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockcontroller.model.Template;
import com.mockcontroller.model.entity.TemplateEntity;
import com.mockcontroller.repository.TemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TemplateService {

    private final TemplateRepository repository;
    private final TemplateMapper mapper;
    private final ObjectMapper objectMapper;

    public TemplateService(TemplateRepository repository, TemplateMapper mapper, ObjectMapper objectMapper) {
        this.repository = repository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public Collection<Template> findAll() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(mapper::toModel)
                .collect(Collectors.toList());
    }

    public List<Template> findBySystemName(String systemName) {
        return repository.findBySystemName(systemName).stream()
                .map(mapper::toModel)
                .collect(Collectors.toList());
    }

    public Optional<Template> findById(String id) {
        return repository.findById(id).map(mapper::toModel);
    }

    @Transactional
    public Template createTemplate(String name, String systemName, JsonNode config, String description) {
        Template template = new Template();
        template.setName(name);
        template.setSystemName(systemName);
        template.setConfig(config);
        template.setDescription(description);
        template.setCreatedAt(Instant.now());

        TemplateEntity entity = mapper.toEntity(template);
        entity = repository.save(entity);
        return mapper.toModel(entity);
    }

    @Transactional
    public Template updateTemplate(String id, String name, JsonNode config, String description) {
        TemplateEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));

        entity.setName(name);
        entity.setDescription(description);
        
        try {
            String configJson = objectMapper.writeValueAsString(config);
            entity.setConfigJson(configJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize config", e);
        }

        entity = repository.save(entity);
        return mapper.toModel(entity);
    }

    @Transactional
    public void deleteTemplate(String id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Template not found: " + id);
        }
        repository.deleteById(id);
    }

    @Transactional
    public void applyTemplate(String templateId, String systemName) {
        Template template = findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        if (!template.getSystemName().equals(systemName)) {
            throw new IllegalArgumentException("Template is for different system: " + template.getSystemName());
        }

        // Применяем шаблон через ConfigService
        // Это будет сделано в контроллере, так как нужен доступ к ConfigService
    }
}

