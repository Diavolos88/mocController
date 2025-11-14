package com.mockcontroller.controller;

import com.mockcontroller.model.CheckUpdateRequest;
import com.mockcontroller.model.CheckUpdateResponse;
import com.mockcontroller.model.ConfigRequest;
import com.mockcontroller.model.ConfigSyncResponse;
import com.mockcontroller.service.ConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/configs")
public class ConfigApiController {

    private final ConfigService configService;

    public ConfigApiController(ConfigService configService) {
        this.configService = configService;
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
}
