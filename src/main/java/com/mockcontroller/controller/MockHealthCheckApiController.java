package com.mockcontroller.controller;

import com.mockcontroller.service.MockStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/healthcheck")
public class MockHealthCheckApiController {

    private final MockStatusService mockStatusService;

    public MockHealthCheckApiController(MockStatusService mockStatusService) {
        this.mockStatusService = mockStatusService;
    }

    @PostMapping
    public ResponseEntity<String> registerHealthcheck(
            @RequestParam String systemName,
            @RequestParam(required = false) String instanceId) {
        mockStatusService.registerHealthcheck(systemName, instanceId);
        return ResponseEntity.ok("OK");
    }

    @PostMapping("/{systemName}")
    public ResponseEntity<String> registerHealthcheckWithPath(
            @PathVariable String systemName,
            @RequestParam(required = false) String instanceId) {
        mockStatusService.registerHealthcheck(systemName, instanceId);
        return ResponseEntity.ok("OK");
    }
}

