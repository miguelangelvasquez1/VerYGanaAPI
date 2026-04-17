package com.verygana2.controllers;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.notification.responses.NotificationResponseDTO;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.notifications.NotificationEmitterRegistry;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationEmitterRegistry emitterRegistry;

    // GET /notifications?page=0&size=20
    @GetMapping
    public ResponseEntity<PagedResponse<NotificationResponseDTO>> getNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = jwt.getClaim("userId");
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(notificationService.getByUserId(userId, pageable));
    }

    // GET /notifications/unread/count
    @GetMapping("/unread/count")
    public ResponseEntity<Long> getUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(notificationService.getCountByUserIdAndReadFalse(userId));
    }

    // PATCH /notifications/read-all
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }

    // GET /notifications/stream  ← SSE
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return emitterRegistry.register(userId);
    }
}
