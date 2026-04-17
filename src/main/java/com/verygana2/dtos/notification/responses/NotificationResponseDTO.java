package com.verygana2.dtos.notification.responses;

import java.time.Instant;
import java.time.ZonedDateTime;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponseDTO {
    private Long id;
    private String type;
    private String title;
    private String message;
    private boolean isRead;
    private ZonedDateTime createdAt;
    private Instant dateSent;
}
