package com.mockcontroller.controller;

import com.mockcontroller.model.*;
import com.mockcontroller.service.ConfigService;
import com.mockcontroller.service.ScheduledConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/configs")
public class ConfigApiController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy");

    private final ConfigService configService;
    private final ScheduledConfigService scheduledConfigService;

    public ConfigApiController(ConfigService configService, ScheduledConfigService scheduledConfigService) {
        this.configService = configService;
        this.scheduledConfigService = scheduledConfigService;
    }

    @PostMapping("/checkUpdate")
    public ResponseEntity<CheckUpdateResponse> checkUpdate(@RequestBody CheckUpdateRequest request) {
        CheckUpdateResponse response = configService.checkUpdate(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ConfigSyncResponse> uploadConfig(@RequestBody ConfigRequest request) {
        ConfigSyncResponse response = configService.handleIncoming(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/schedule")
    public ResponseEntity<ScheduleUpdateResponse> scheduleUpdate(@RequestBody ScheduleUpdateRequest request) {
        try {
            // Парсим дату и время
            LocalDateTime scheduledTime;
            try {
                scheduledTime = LocalDateTime.parse(request.getScheduledTime(), DATE_TIME_FORMATTER);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(
                    new ScheduleUpdateResponse(false, "Неверный формат даты. Используйте: HH:mm:ss dd-MM-yyyy", null, null)
                );
            }

            // Проверяем, что дата в будущем
            if (scheduledTime.isBefore(LocalDateTime.now())) {
                return ResponseEntity.badRequest().body(
                    new ScheduleUpdateResponse(false, "Дата должна быть в будущем", null, null)
                );
            }

            // Создаем конфиг из запроса
            com.fasterxml.jackson.databind.JsonNode newConfig = request.getConfig();

            // Планируем обновление
            ScheduledConfigUpdate update = scheduledConfigService.scheduleUpdate(
                request.getSystemName(),
                newConfig,
                scheduledTime,
                request.getComment()
            );

            return ResponseEntity.ok(new ScheduleUpdateResponse(
                true,
                "Обновление успешно запланировано",
                update.getId(),
                scheduledTime.format(DATE_TIME_FORMATTER)
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ScheduleUpdateResponse(false, "Ошибка: " + e.getMessage(), null, null)
            );
        }
    }

    @GetMapping("/{systemName}/scheduled")
    public ResponseEntity<List<ScheduledUpdateInfo>> getScheduledUpdates(@PathVariable String systemName) {
        try {
            List<ScheduledConfigUpdate> updates = scheduledConfigService.getScheduledUpdates(systemName);
            List<ScheduledUpdateInfo> result = updates.stream()
                .map(update -> new ScheduledUpdateInfo(
                    update,
                    update.getScheduledTime().format(DATE_TIME_FORMATTER),
                    update.getCreatedAt() != null ? update.getCreatedAt().format(DATE_TIME_FORMATTER) : null
                ))
                .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/scheduled/{updateId}")
    public ResponseEntity<ScheduleUpdateResponse> cancelScheduledUpdate(@PathVariable String updateId) {
        try {
            scheduledConfigService.cancelScheduledUpdate(updateId);
            return ResponseEntity.ok(new ScheduleUpdateResponse(
                true,
                "Запланированное обновление отменено",
                updateId,
                null
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ScheduleUpdateResponse(false, "Ошибка: " + e.getMessage(), updateId, null)
            );
        }
    }
}
