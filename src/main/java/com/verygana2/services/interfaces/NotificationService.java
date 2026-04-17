package com.verygana2.services.interfaces;

import java.time.Instant;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.notification.responses.NotificationResponseDTO;

public interface NotificationService {

    PagedResponse<NotificationResponseDTO> getByUserId(Long userId, Pageable pageable);

    long getCountByUserIdAndReadFalse(Long userId);

    void createInternalNotification (Long consumerId, String title, String message, Instant dateSent);

    int markAllAsRead(Long userId);
}
