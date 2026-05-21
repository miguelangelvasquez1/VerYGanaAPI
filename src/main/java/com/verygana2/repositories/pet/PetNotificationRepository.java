package com.verygana2.repositories.pet;

import com.verygana2.models.pets.PetNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PetNotificationRepository extends JpaRepository<PetNotification, Long> {
    List<PetNotification> findAllByActiveTrueAndReadFalse();
    Optional<PetNotification> findByExternalId(String externalId);
}