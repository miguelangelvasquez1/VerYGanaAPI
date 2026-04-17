package com.verygana2.services.notifications;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
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
    private final NotificationEmitterRegistry emitterRegistry;

    @Override
    public PagedResponse<NotificationResponseDTO> getByUserId(Long userId, Pageable pageable) {

        return PagedResponse.from(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(notificationMapper::toNotificationResponseDTO));
    }

    @Override
    public long getCountByUserIdAndReadFalse(Long userId) {

        if (userId == null) {
            throw new IllegalArgumentException("invalid userId");
        }
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsReadByUserId(userId);
    }

    @Override
    @Async("notificationExecutor")
    public void createInternalNotification(Long consumerId, String title, String message,
            Instant dateSent) {

        validateNotificationInput(consumerId, title, message, dateSent);

        try {
            ConsumerDetails consumer = consumerDetailsService.getConsumerById(consumerId);

            Notification notification = Notification.builder()
                    .type(NotificationType.IN_APP_NOTIFICATION)
                    .user(consumer)
                    .title(title)
                    .message(message)
                    .dateSent(dateSent)
                    .build();

            Notification savedNotification = notificationRepository.save(notification);

            log.info("✅ Notification created: ID={}, ConsumerId={}",
                    savedNotification.getId(), consumerId);

            NotificationResponseDTO dto = notificationMapper.toNotificationResponseDTO(savedNotification);
            emitterRegistry.send(consumerId, dto);

        } catch (NotificationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create notification for consumer {}: {}", consumerId, e.getMessage());
            throw new NotificationException("Failed to create notification: " + e.getMessage());
        }
    }

    private void validateNotificationInput(Long consumerId, String title,
            String message, Instant dateSent) {
        if (consumerId == null || consumerId <= 0)
            throw new NotificationException("Consumer ID must be positive");
        if (title == null || title.isBlank())
            throw new NotificationException("Title is required");
        if (title.length() > 200)
            throw new NotificationException("Title cannot exceed 200 characters");
        if (message == null || message.isBlank())
            throw new NotificationException("Message is required");
        if (message.length() > 1000)
            throw new NotificationException("Message cannot exceed 1000 characters");
        if (dateSent == null)
            throw new NotificationException("Date sent is required");
    }

}
