package com.verygana2.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.notification.responses.NotificationResponseDTO;
import com.verygana2.services.interfaces.NotificationService;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponseDTO>> getByUserIdOrderByDateSentDesc(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(notificationService.getByUserIdOrderByDateSentDesc(userId));
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Long> getCountByUserIdAndReadFalse(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(notificationService.getCountByUserIdAndReadFalse(userId));
    }
}
