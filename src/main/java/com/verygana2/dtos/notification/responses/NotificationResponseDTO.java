package com.verygana2.dtos.notification.responses;

import java.time.Instant;

import com.verygana2.models.enums.NotificationType;

import lombok.Data;

@Data
public class NotificationResponseDTO {
    
    private Long id;

    private String userId;

    private NotificationType type;

    private String title;

    private String message;

    private boolean isRead;

    private Instant dateSent;
}
