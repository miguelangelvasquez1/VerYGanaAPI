package com.verygana2.services.interfaces.pet;

import com.verygana2.dtos.pet.PetNotificationRequestDTO;
import com.verygana2.dtos.pet.PetNotificationResponseDTO;
import java.util.List;

public interface PetNotificationService {
    List<PetNotificationResponseDTO> getAllNotifications();
    List<PetNotificationResponseDTO> getAllNotificationsAdmin();
    PetNotificationResponseDTO createNotification(PetNotificationRequestDTO dto);
    PetNotificationResponseDTO updateNotification(Long id, PetNotificationRequestDTO dto);
    void deleteNotification(Long id);
    void markNotificationAsRead(String externalId);
}