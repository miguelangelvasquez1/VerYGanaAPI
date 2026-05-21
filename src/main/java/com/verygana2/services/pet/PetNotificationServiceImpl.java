package com.verygana2.services.pet;



import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.pet.PetNotificationRequestDTO;
import com.verygana2.dtos.pet.PetNotificationResponseDTO;

import com.verygana2.mappers.pet.PetNotificationMapper;
import com.verygana2.models.pets.PetNotification;
import com.verygana2.repositories.pet.PetNotificationRepository;
import com.verygana2.services.interfaces.pet.PetNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PetNotificationServiceImpl implements PetNotificationService {

    private final PetNotificationRepository notificationRepository;
    private final PetNotificationMapper notificationMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<PetNotificationResponseDTO> getAllNotifications() {
        return notificationRepository.findAllByActiveTrueAndReadFalse()
                .stream().map(notificationMapper::toResponseDTO).toList();
    }

    @Override
    public PetNotificationResponseDTO createNotification(PetNotificationRequestDTO dto) {
        return notificationMapper.toResponseDTO(
                notificationRepository.save(notificationMapper.toEntity(dto))
        );
    }

    @Override
    public PetNotificationResponseDTO updateNotification(Long id, PetNotificationRequestDTO dto) {
        PetNotification notif = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notificationMapper.updateFromDto(dto, notif);
        return notificationMapper.toResponseDTO(notificationRepository.save(notif));
    }

    @Override
    public void markNotificationAsRead(String externalId) {
        PetNotification notif = notificationRepository.findByExternalId(externalId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notif.setRead(true);
        notificationRepository.save(notif);
    }


}