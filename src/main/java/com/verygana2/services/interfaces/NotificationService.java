package com.verygana2.services.interfaces;

import java.time.Instant;
import java.util.List;

import com.verygana2.dtos.notification.responses.NotificationResponseDTO;

public interface NotificationService {
    List<NotificationResponseDTO> getByUserIdOrderByDateSentDesc(Long userId);
    long getCountByUserIdAndReadFalse(Long userId);
    void createInternalNotification (Long consumerId, String title, String message, Instant dateSent);
}
