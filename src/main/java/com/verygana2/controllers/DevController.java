package com.verygana2.controllers;

import com.verygana2.services.interfaces.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dev")
public class DevController {

    private final OutboxService outboxService;

    @PostMapping("/process-outbox")
    public ResponseEntity<String> processOutbox() {
        outboxService.processOutboxEvents();
        return ResponseEntity.ok("Outbox processed");
    }
}