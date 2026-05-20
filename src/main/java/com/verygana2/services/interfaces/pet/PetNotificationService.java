package com.verygana2.services.interfaces.pet;

import com.verygana2.dtos.pet.PetNotificationRequestDTO;
import com.verygana2.dtos.pet.PetNotificationResponseDTO;
import java.util.List;

public interface PetNotificationService {
    List<PetNotificationResponseDTO> getAllNotifications();
    PetNotificationResponseDTO createNotification(PetNotificationRequestDTO dto);
    PetNotificationResponseDTO updateNotification(Long id, PetNotificationRequestDTO dto);
    void markNotificationAsRead(String externalId);

}