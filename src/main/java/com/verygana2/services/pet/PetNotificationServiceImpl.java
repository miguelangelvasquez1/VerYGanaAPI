package com.verygana2.services.pet;

import com.verygana2.dtos.pet.PetNotificationRequestDTO;
import com.verygana2.dtos.pet.PetNotificationResponseDTO;
import com.verygana2.mappers.pet.PetNotificationMapper;
import com.verygana2.models.pets.PetNotification;
import com.verygana2.repositories.pet.PetNotificationRepository;
import com.verygana2.services.interfaces.pet.PetNotificationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PetNotificationServiceImpl implements PetNotificationService {

    private final PetNotificationRepository notificationRepository;
    private final PetNotificationMapper notificationMapper;

    @Override
    public List<PetNotificationResponseDTO> getAllNotifications() {
        return notificationRepository.findAllByActiveTrueAndReadFalse()
                .stream().map(notificationMapper::toResponseDTO).toList();
    }

    @Override
    public List<PetNotificationResponseDTO> getAllNotificationsAdmin() {
        return notificationRepository.findAll()
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
                .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + id));
        notificationMapper.updateFromDto(dto, notif);
        return notificationMapper.toResponseDTO(notificationRepository.save(notif));
    }

    @Override
    public void deleteNotification(Long id) {
        PetNotification notif = notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + id));
        notif.setActive(false);
        notificationRepository.save(notif);
    }

    @Override
    public void markNotificationAsRead(String externalId) {
        PetNotification notif = notificationRepository.findByExternalId(externalId)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + externalId));
        notif.setRead(true);
        notificationRepository.save(notif);
    }
}