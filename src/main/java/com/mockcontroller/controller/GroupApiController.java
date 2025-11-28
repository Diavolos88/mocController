package com.mockcontroller.controller;

import com.mockcontroller.model.Group;
import com.mockcontroller.model.GroupRequest;
import com.mockcontroller.service.GroupService;
import com.mockcontroller.service.MockStatusService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
public class GroupApiController {

    private final GroupService groupService;
    private final MockStatusService mockStatusService;

    public GroupApiController(GroupService groupService, MockStatusService mockStatusService) {
        this.groupService = groupService;
        this.mockStatusService = mockStatusService;
    }

    @GetMapping
    public ResponseEntity<Collection<Group>> getAllGroups() {
        try {
            return ResponseEntity.ok(groupService.findAll());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Group> getGroup(@PathVariable String id) {
        try {
            return groupService.findById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping
    public ResponseEntity<?> createGroup(@RequestBody GroupRequest request) {
        try {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Название группы обязательно");
            }

            Group group = groupService.createGroup(
                    request.getName(),
                    request.getDescription(),
                    request.getSystemNames()
            );
            return ResponseEntity.ok(group);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при создании группы: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateGroup(@PathVariable String id, @RequestBody GroupRequest request) {
        try {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Название группы обязательно");
            }

            Group group = groupService.updateGroup(
                    id,
                    request.getName(),
                    request.getDescription(),
                    request.getSystemNames()
            );
            return ResponseEntity.ok(group);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при обновлении группы: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String id) {
        try {
            groupService.deleteGroup(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/healthcheck")
    public ResponseEntity<?> checkGroupHealth(@RequestParam String groupName) {
        try {
            if (groupName == null || groupName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Название группы обязательно");
            }

            Group group = groupService.findByName(groupName)
                    .orElseThrow(() -> new IllegalArgumentException("Группа не найдена: " + groupName));

            MockStatusService.GroupHealthCheckResult result = mockStatusService.checkGroupHealth(group.getSystemNames());

            if (result.isAllHealthy()) {
                // Все системы имеют хотя бы одну онлайн под
                Map<String, Object> response = new HashMap<>();
                response.put("status", "OK");
                response.put("message", "Все заглушки запущены");
                response.put("groupName", group.getName());
                return ResponseEntity.ok(response);
            } else {
                // Не все системы имеют онлайн поды - возвращаем детальную статистику
                Map<String, Object> response = new HashMap<>();
                response.put("status", "NOT_ALL_HEALTHY");
                response.put("message", "Не все системы в группе имеют онлайн поды");
                response.put("groupName", group.getName());
                response.put("systems", result.getSystems().stream()
                        .map(sys -> {
                            Map<String, Object> sysInfo = new HashMap<>();
                            sysInfo.put("systemName", sys.getSystemName());
                            sysInfo.put("hasOnlinePods", sys.isHasOnlinePods());
                            sysInfo.put("onlineCount", sys.getOnlineCount());
                            sysInfo.put("offlineCount", sys.getOfflineCount());
                            sysInfo.put("totalCount", sys.getTotalCount());
                            return sysInfo;
                        })
                        .toList());

                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Группа не найдена: " + groupName);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при проверке статуса группы: " + e.getMessage());
        }
    }
}

