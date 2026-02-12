package com.verygana2.services;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;


import com.verygana2.dtos.notification.responses.NotificationResponseDTO;
import com.verygana2.exceptions.notificationExceptions.NotificationException;
import com.verygana2.mappers.NotificationMapper;
import com.verygana2.models.Notification;
import com.verygana2.models.enums.NotificationType;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.NotificationRepository;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final ConsumerDetailsService consumerDetailsService;

    @Override
    public long getCountByUserIdAndReadFalse(Long userId) {

        if (userId == null) {
            throw new IllegalArgumentException("invalid userId");
        }
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    public List<NotificationResponseDTO> getByUserIdOrderByDateSentDesc(Long userId) {
        
        List<Notification> notifications = notificationRepository.findByUserIdOrderByDateSentDesc(userId);
        return notifications.stream().map(notificationMapper::toNotificationResponseDTO).toList();
    }

    @Override
    @Async("notificationExecutor")
    public void createInternalNotification(Long consumerId, String title, String message,
            Instant dateSent) {

        if (consumerId == null || consumerId <= 0) {
            throw new NotificationException("Consumer ID must be positive");
        }

        if (title == null || title.isBlank()) {
            throw new NotificationException("Title is required");
        }

        if (title.length() > 200) { // ✅ Validar longitud
            throw new NotificationException("Title cannot exceed 200 characters");
        }

        if (message == null || message.isBlank()) {
            throw new NotificationException("Message is required");
        }

        if (message.length() > 1000) { // ✅ Validar longitud
            throw new NotificationException("Message cannot exceed 1000 characters");
        }

        if (dateSent == null) {
            throw new NotificationException("Date sent is required");
        }

        try {
            ConsumerDetails consumer = consumerDetailsService.getConsumerById(consumerId);

            Notification notification = new Notification();
            notification.setType(NotificationType.IN_APP_NOTIFICATION);
            notification.setUser(consumer);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setDateSent(dateSent);

            Notification savedNotification = notificationRepository.save(notification);

            log.info("✅ Notification created: ID={}, ConsumerId={}",
                    savedNotification.getId(), consumerId);

        } catch (Exception e) {
            log.error("Failed to create notification for consumer {}: {}", consumerId, e.getMessage());
            throw new NotificationException("Failed to create notification: " + e.getMessage());
        }
    }
}
