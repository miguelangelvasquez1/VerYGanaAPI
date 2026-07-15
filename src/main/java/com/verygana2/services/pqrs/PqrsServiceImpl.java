package com.verygana2.services.pqrs;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.config.pqrs.PqrsSlaProperties;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.pqrs.requests.CreatePqrsRequestDTO;
import com.verygana2.dtos.pqrs.requests.RespondPqrsRequestDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAdminDetailDTO;
import com.verygana2.dtos.pqrs.responses.PqrsResponseDTO;
import com.verygana2.exceptions.pqrsExceptions.PqrsAccessDeniedException;
import com.verygana2.mappers.pqrs.PqrsMapper;
import com.verygana2.models.User;
import com.verygana2.models.enums.pqrs.PqrsStatus;
import com.verygana2.models.enums.pqrs.PqrsType;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.repositories.UserRepository;
import com.verygana2.repositories.pqrs.PqrsRepository;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.pqrs.PqrsService;
import com.verygana2.utils.audit.AuditLevel;
import com.verygana2.utils.audit.Auditable;
import com.verygana2.utils.pqrs.BusinessDayCalculator;
import com.verygana2.utils.pqrs.RequesterNameResolver;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PqrsServiceImpl implements PqrsService {

    private final PqrsRepository pqrsRepository;
    private final UserRepository userRepository;
    private final PqrsAssignmentService pqrsAssignmentService;
    private final PqrsMapper pqrsMapper;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final BusinessDayCalculator businessDayCalculator;
    private final PqrsSlaProperties pqrsSlaProperties;
    private final RequesterNameResolver requesterNameResolver;

    @Override
    @Auditable(action = "PQRS_SUBMIT", level = AuditLevel.INFO, category = "PQRS", description = "Usuario radica un PQRS")
    public PqrsResponseDTO createPqrs(CreatePqrsRequestDTO dto, Long requesterUserId) {
        User requester = userRepository.findById(requesterUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + requesterUserId));

        ZonedDateTime dueDate = businessDayCalculator.addBusinessDays(
                ZonedDateTime.now(), pqrsSlaProperties.getSlaDaysFor(dto.getType()));

        Optional<AdminDetails> assignedAdmin = pqrsAssignmentService.pickNextAdmin();

        Pqrs pqrs = Pqrs.builder()
                .type(dto.getType())
                .requester(requester)
                .assignedAdmin(assignedAdmin.orElse(null))
                .subject(dto.getSubject())
                .description(dto.getDescription())
                .dueDate(dueDate)
                .build();

        Pqrs saved = pqrsRepository.save(pqrs);
        log.info("PQRS {} creado por userId={}, status={}", saved.getId(), requesterUserId, saved.getStatus());

        assignedAdmin.ifPresent(admin -> notifyAdminOfAssignment(saved, admin));

        emailService.sendPqrsReceivedConfirmation(requester.getEmail(), requesterNameResolver.resolve(requester),
                saved.getBased(), saved.getType(), saved.getDueDate());

        return pqrsMapper.toResponseDTO(saved);
    }

    @Override
    public PagedResponse<PqrsResponseDTO> getMyPqrs(Long requesterUserId, Pageable pageable) {
        return PagedResponse.from(pqrsRepository.findByRequesterId(requesterUserId, pageable)
                .map(pqrsMapper::toResponseDTO));
    }

    @Override
    public PqrsResponseDTO getMyPqrsDetail(Long pqrsId, Long requesterUserId) {
        Pqrs pqrs = pqrsRepository.findById(pqrsId)
                .orElseThrow(() -> new EntityNotFoundException("PQRS not found: " + pqrsId));

        if (!pqrs.getRequester().getId().equals(requesterUserId)) {
            throw new EntityNotFoundException("PQRS not found: " + pqrsId);
        }

        return pqrsMapper.toResponseDTO(pqrs);
    }

    @Override
    public PagedResponse<PqrsAdminDetailDTO> getAssignedPqrs(Long adminUserId, PqrsStatus status, PqrsType type,
            Pageable pageable) {
        return PagedResponse.from(pqrsRepository.findByAssignedAdminWithFilters(adminUserId, status, type, pageable)
                .map(pqrsMapper::toAdminDetailDTO));
    }

    @Override
    public PqrsAdminDetailDTO getPqrsDetailForAdmin(Long pqrsId, Long adminUserId) {
        Pqrs pqrs = loadOwnedByAdmin(pqrsId, adminUserId);
        return pqrsMapper.toAdminDetailDTO(pqrs);
    }

    @Override
    @Auditable(action = "PQRS_REVIEW", level = AuditLevel.INFO, category = "PQRS", description = "Admin marca un PQRS en revisión")
    public void markUnderReview(Long pqrsId, Long adminUserId) {
        Pqrs pqrs = loadOwnedByAdmin(pqrsId, adminUserId);

        if (!pqrs.canBeReviewed()) {
            throw new ValidationException("PQRS cannot be reviewed from status: " + pqrs.getStatus());
        }

        pqrs.setStatus(PqrsStatus.EN_REVISION);
        pqrsRepository.save(pqrs);
    }

    @Override
    @Auditable(action = "PQRS_RESPOND", level = AuditLevel.INFO, category = "PQRS", description = "Admin resuelve un PQRS")
    public void respondToPqrs(Long pqrsId, RespondPqrsRequestDTO dto, Long adminUserId) {
        Pqrs pqrs = loadOwnedByAdmin(pqrsId, adminUserId);

        if (!pqrs.canBeResolved()) {
            throw new ValidationException("PQRS cannot be resolved from status: " + pqrs.getStatus());
        }

        pqrs.setResponse(dto.getResponse());
        pqrs.setStatus(PqrsStatus.RESUELTA);
        pqrs.setResolvedAt(ZonedDateTime.now());
        Pqrs saved = pqrsRepository.save(pqrs);

        User requester = saved.getRequester();
        notificationService.createInternalNotification(
                requester.getId(),
                "Tu PQRS fue resuelto",
                "Radicado " + saved.getBased() + ": " + dto.getResponse(),
                Instant.now());

        emailService.sendPqrsResolved(requester.getEmail(), requesterNameResolver.resolve(requester),
                saved.getBased(), dto.getResponse());
    }

    private Pqrs loadOwnedByAdmin(Long pqrsId, Long adminUserId) {
        Pqrs pqrs = pqrsRepository.findById(pqrsId)
                .orElseThrow(() -> new EntityNotFoundException("PQRS not found: " + pqrsId));

        if (pqrs.getAssignedAdmin() == null || !pqrs.getAssignedAdmin().getUser().getId().equals(adminUserId)) {
            throw new PqrsAccessDeniedException("Este PQRS no está asignado a este administrador");
        }

        return pqrs;
    }

    @Override
    public void retryPendingAssignments() {
        var pending = pqrsRepository.findByStatus(PqrsStatus.PENDIENTE_ASIGNACION);
        if (pending.isEmpty()) return;

        log.info("Reintentando asignación de {} PQRS pendientes", pending.size());
        for (Pqrs pqrs : pending) {
            pqrsAssignmentService.pickNextAdmin().ifPresent(admin -> {
                pqrs.setAssignedAdmin(admin);
                pqrs.setStatus(PqrsStatus.RECIBIDA);
                Pqrs saved = pqrsRepository.save(pqrs);
                notifyAdminOfAssignment(saved, admin);
            });
        }
    }

    @Override
    public void sendSlaAlerts(int daysBeforeDueDateToAlert) {
        ZonedDateTime alertThreshold = ZonedDateTime.now().plusDays(daysBeforeDueDateToAlert);
        var atRisk = pqrsRepository.findByStatusInAndDueDateBefore(
                java.util.List.of(PqrsStatus.RECIBIDA, PqrsStatus.EN_REVISION), alertThreshold);

        for (Pqrs pqrs : atRisk) {
            AdminDetails admin = pqrs.getAssignedAdmin();
            if (admin == null) continue;

            emailService.sendPqrsSlaAlert(
                    admin.getUser().getEmail(),
                    requesterNameResolver.resolve(admin.getUser()),
                    pqrs.getBased(),
                    pqrs.getDueDate());
        }
    }

    private void notifyAdminOfAssignment(Pqrs pqrs, AdminDetails admin) {
        notificationService.createInternalNotification(
                admin.getUser().getId(),
                "Nuevo PQRS asignado",
                "Radicado " + pqrs.getBased() + ": " + pqrs.getSubject(),
                Instant.now());

        emailService.sendPqrsAssignedToAdmin(
                admin.getUser().getEmail(),
                requesterNameResolver.resolve(admin.getUser()),
                pqrs.getBased(),
                pqrs.getSubject(),
                pqrs.getDueDate());
    }
}
