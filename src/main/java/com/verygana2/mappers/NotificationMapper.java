package com.verygana2.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.notification.responses.NotificationResponseDTO;
import com.verygana2.models.Notification;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    @Mapping(target = "userId", source = "user.id")
    NotificationResponseDTO toNotificationResponseDTO (Notification notification);
}
