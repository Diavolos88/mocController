package com.mockcontroller.controller;

import com.mockcontroller.model.Template;
import com.mockcontroller.model.TemplateRequest;
import com.mockcontroller.service.ConfigService;
import com.mockcontroller.service.TemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api/templates")
public class TemplateApiController {

    private final TemplateService templateService;
    private final ConfigService configService;

    public TemplateApiController(TemplateService templateService, ConfigService configService) {
        this.templateService = templateService;
        this.configService = configService;
    }

    @GetMapping
    public ResponseEntity<Collection<Template>> getAllTemplates(
            @RequestParam(required = false) String systemName) {
        try {
            Collection<Template> templates;
            if (systemName != null && !systemName.isEmpty()) {
                templates = templateService.findBySystemName(systemName);
            } else {
                templates = templateService.findAll();
            }
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/by-system/{system}")
    public ResponseEntity<Collection<Template>> getTemplatesBySystem(@PathVariable String system) {
        try {
            Collection<Template> allTemplates = templateService.findAll();
            // Фильтруем шаблоны по системе (по префиксу)
            java.util.List<Template> filteredTemplates = allTemplates.stream()
                    .filter(template -> {
                        String templateSystem = template.getSystemName();
                        if (templateSystem == null) return false;
                        // Проверяем, соответствует ли шаблон системе
                        if (isValidTemplate(templateSystem)) {
                            String systemPrefix = extractSystemPrefix(templateSystem);
                            return systemPrefix.equals(system);
                        } else {
                            return templateSystem.equals(system);
                        }
                    })
                    .collect(java.util.stream.Collectors.toList());
            return ResponseEntity.ok(filteredTemplates);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean isValidTemplate(String systemName) {
        if (systemName == null || systemName.isEmpty()) {
            return false;
        }
        int dashCount = 0;
        for (char c : systemName.toCharArray()) {
            if (c == '-') {
                dashCount++;
            }
        }
        return dashCount >= 2 && systemName.endsWith("-mock");
    }

    private String extractSystemPrefix(String systemName) {
        int firstDashIndex = systemName.indexOf('-');
        if (firstDashIndex > 0) {
            return systemName.substring(0, firstDashIndex);
        }
        return systemName;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Template> getTemplate(@PathVariable String id) {
        try {
            return templateService.findById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping
    public ResponseEntity<?> createTemplate(@RequestBody TemplateRequest request) {
        try {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Название шаблона обязательно");
            }
            if (request.getSystemName() == null || request.getSystemName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Название системы обязательно");
            }
            if (request.getConfig() == null) {
                return ResponseEntity.badRequest().body("Конфигурация обязательна");
            }

            Template template = templateService.createTemplate(
                    request.getName(),
                    request.getSystemName(),
                    request.getConfig(),
                    request.getDescription()
            );
            return ResponseEntity.ok(template);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при создании шаблона: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Template> updateTemplate(
            @PathVariable String id,
            @RequestBody TemplateRequest request) {
        try {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            if (request.getConfig() == null) {
                return ResponseEntity.badRequest().build();
            }

            Template template = templateService.updateTemplate(
                    id,
                    request.getName(),
                    request.getConfig(),
                    request.getDescription()
            );
            return ResponseEntity.ok(template);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String id) {
        try {
            templateService.deleteTemplate(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/apply")
    public ResponseEntity<Void> applyTemplate(
            @PathVariable String id,
            @RequestParam String systemName) {
        try {
            Template template = templateService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Template not found"));

            if (!template.getSystemName().equals(systemName)) {
                return ResponseEntity.badRequest().build();
            }

            // Применяем шаблон к конфигу
            configService.updateCurrentConfig(systemName, template.getConfig());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

