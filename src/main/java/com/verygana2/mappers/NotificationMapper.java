package com.verygana2.mappers;

import org.mapstruct.Mapper;

import com.verygana2.dtos.notification.responses.NotificationResponseDTO;
import com.verygana2.models.Notification;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    NotificationResponseDTO toNotificationResponseDTO (Notification notification);
}
