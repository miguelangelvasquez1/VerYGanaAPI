package com.verygana2.services.pqrs;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.repositories.details.AdminDetailsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Asigna cada PQRS al admin activo que lleva más tiempo esperando su turno
 * (round-robin por "menos asignaciones recientes"). El SELECT ... FOR UPDATE
 * serializa asignaciones concurrentes para que dos PQRS simultáneos nunca
 * caigan en el mismo admin.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PqrsAssignmentService {

    private final AdminDetailsRepository adminDetailsRepository;

    @Transactional
    public Optional<AdminDetails> pickNextAdmin() {
        List<AdminDetails> candidates = adminDetailsRepository
                .findActiveAdminsForPqrsAssignmentForUpdate(PageRequest.of(0, 1));

        if (candidates.isEmpty()) {
            log.warn("No hay admins activos disponibles para asignar un PQRS");
            return Optional.empty();
        }

        AdminDetails admin = candidates.get(0);
        admin.setLastPqrsAssignedAt(ZonedDateTime.now());
        AdminDetails saved = adminDetailsRepository.save(admin);

        log.info("PQRS asignado por rotación al admin userId={}", saved.getUser().getId());
        return Optional.of(saved);
    }
}
