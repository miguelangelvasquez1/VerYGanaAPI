package com.verygana2.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.branding.AddCommentDTO;
import com.verygana2.dtos.branding.ApproveBrandingRequestDTO;
import com.verygana2.dtos.branding.BrandingRequestCommentDTO;
import com.verygana2.dtos.branding.AssignDesignerDTO;
import com.verygana2.dtos.branding.BrandingGameDTO;
import com.verygana2.dtos.branding.BrandingRequestDetailDTO;
import com.verygana2.dtos.branding.BrandingRequestSummaryDTO;
import com.verygana2.dtos.branding.ConfirmCorporateResourceDTO;
import com.verygana2.dtos.branding.CorporateResourceDTO;
import com.verygana2.dtos.branding.CorporateResourceUploadPermissionDTO;
import com.verygana2.dtos.branding.CreateBrandingRequestDTO;
import com.verygana2.dtos.branding.GameDesignerSummaryDTO;
import com.verygana2.dtos.branding.RejectBrandingRequestDTO;
import com.verygana2.dtos.branding.UpdateBrandingRequestConfigDTO;
import com.verygana2.mappers.BrandingMapper;
import com.verygana2.models.Category;
import com.verygana2.models.Municipality;
import com.verygana2.models.branding.BrandingRequest;
import com.verygana2.models.branding.CorporateResource;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.BrandingRequestStatus;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.games.Game;
import com.verygana2.models.branding.BrandingRequestComment;
import com.verygana2.models.enums.CommentAuthorRole;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.GameDesignerDetails;
import com.verygana2.repositories.branding.BrandingRequestCommentRepository;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.repositories.branding.CorporateResourceRepository;
import com.verygana2.repositories.details.AdminDetailsRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.details.GameDesignerDetailsRepository;
import com.verygana2.repositories.games.GameRepository;
import com.verygana2.services.interfaces.BrandingRequestService;
import com.verygana2.services.interfaces.CategoryService;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.GameService;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.storage.service.R2Service;
import com.verygana2.utils.validators.TargetingValidator;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class BrandingRequestServiceImpl implements BrandingRequestService {

    private static final long MAX_RESOURCE_SIZE_BYTES = 1024 * 1024 * 20L;
    private static final Set<SupportedMimeType> ALLOWED_RESOURCE_TYPES = Set.of(
        SupportedMimeType.IMAGE_PNG,
        SupportedMimeType.IMAGE_JPEG,
        SupportedMimeType.IMAGE_JPG,
        SupportedMimeType.IMAGE_WEBP
    );

    @Value("${app.admin-notification-email:admin@verygana.com}")
    private String adminNotificationEmail;

    private final BrandingRequestRepository brandingRequestRepository;
    private final BrandingRequestCommentRepository commentRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final GameRepository gameRepository;
    private final AdminDetailsRepository adminDetailsRepository;
    private final GameDesignerDetailsRepository gameDesignerDetailsRepository;
    private final CorporateResourceRepository corporateResourceRepository;
    private final CategoryService categoryService;
    private final TargetingValidator targetingValidator;
    private final R2Service r2Service;
    private final BrandingMapper brandingMapper;
    private final GameService gameService;
    private final EmailService emailService;
    private final NotificationService notificationService;

    // ===== CATÁLOGO DE JUEGOS =====

    @Override
    @Transactional(readOnly = true)
    public Page<BrandingGameDTO> getGamesForBranding(Pageable pageable) {
        return gameRepository.findByActiveTrue(pageable).map(brandingMapper::toBrandingGameDTO);
    }

    // ===== ANUNCIANTE =====

    @Override
    @Transactional(readOnly = true)
    public BrandingRequestDetailDTO getBrandingRequestDetail(Long requestId, Long userId) {
        BrandingRequest request = findOwnedRequest(requestId, userId);

        BrandingRequestDetailDTO detail = brandingMapper.toDetailDTO(request);

        List<CorporateResourceDTO> resources = request.getCorporateResources().stream()
            .map(resource -> {
                CorporateResourceDTO dto = brandingMapper.toCorporateResourceDTO(resource);
                if (resource.getStatus() == AssetStatus.VALIDATED) {
                    dto.setTemporalUrl(r2Service.getPrivateObject(resource.getObjectKey(), 1800));
                }
                return dto;
            })
            .collect(Collectors.toList());

        detail.setCorporateResources(resources);
        return detail;
    }

    @Override
    public BrandingRequestSummaryDTO createBrandingRequest(CreateBrandingRequestDTO dto, Long commercialUserId) {
        CommercialDetails commercial = commercialDetailsRepository.findByUser_Id(commercialUserId)
            .orElseThrow(() -> new EntityNotFoundException("Commercial profile not found for user: " + commercialUserId));

        Game game = gameRepository.findById(dto.getGameId())
            .orElseThrow(() -> new EntityNotFoundException("Game not found: " + dto.getGameId()));

        BigDecimal scoreRewardFactor = null;
        Long averageRewardPerSessionCents = null;
        Long estimatedSessions = null;
        Long completionRewardCents = null;
        Long maxRewardPerSessionCents = null;
        if (game.getConfigDefinitions() != null) {
            var latestConfig = game.getConfigDefinitions().stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsLatest()))
                .findFirst()
                .orElse(null);

            if (latestConfig != null) {
                if (latestConfig.getScoreRewardFactor() != null) {
                    scoreRewardFactor = BigDecimal.valueOf(latestConfig.getScoreRewardFactor());
                }
                averageRewardPerSessionCents = latestConfig.getAverageRewardPerSessionCents();
                if (averageRewardPerSessionCents != null && averageRewardPerSessionCents > 0) {
                    estimatedSessions = dto.getBudgetCents() / averageRewardPerSessionCents;
                }
                completionRewardCents = latestConfig.getCompletionRewardCents();
                maxRewardPerSessionCents = latestConfig.getMaxRewardPerSessionCents();
            }
        }

        BrandingRequest request = BrandingRequest.builder()
            .commercial(commercial)
            .game(game)
            .brandName(dto.getBrandName())
            .brandDescription(dto.getBrandDescription())
            .targetUrl(dto.getTargetUrl())
            .budgetCents(dto.getBudgetCents())
            .scoreRewardFactor(scoreRewardFactor)
            .averageRewardPerSessionCents(averageRewardPerSessionCents)
            .estimatedSessions(estimatedSessions)
            .completionRewardCents(completionRewardCents)
            .maxRewardPerSessionCents(maxRewardPerSessionCents)
            .status(BrandingRequestStatus.DRAFT)
            .build();

        BrandingRequest saved = brandingRequestRepository.save(request);
        log.info("BrandingRequest {} created in DRAFT by commercial user {}", saved.getId(), commercialUserId);
        return brandingMapper.toSummaryDTO(saved);
    }

    @Override
    public void submitForReview(Long requestId, Long userId, String notes) {
        BrandingRequest request = findOwnedRequest(requestId, userId);

        if (!request.canBeSubmitted()) {
            throw new ValidationException("Request cannot be submitted from status: " + request.getStatus());
        }

        long validatedResources = request.getCorporateResources().stream()
            .filter(r -> r.getStatus() == AssetStatus.VALIDATED)
            .count();

        if (validatedResources == 0) {
            throw new IllegalStateException("At least one corporate resource must be uploaded and confirmed before submitting");
        }

        request.setStatus(BrandingRequestStatus.PENDING_REVIEW);
        log.info("BrandingRequest {} submitted for review by commercial user {}", requestId, userId);

        if (notes != null && !notes.isBlank()) {
            commentRepository.save(BrandingRequestComment.builder()
                    .brandingRequest(request)
                    .content(notes)
                    .authorUserId(userId)
                    .authorName(request.getCommercial().getCompanyName())
                    .authorRole(CommentAuthorRole.COMMERCIAL)
                    .relatedStatus(BrandingRequestStatus.PENDING_REVIEW)
                    .build());
        }
    }

    // segundo paso o update
    // Reglas de editabilidad por status:
    // Status	¿Editable?	Secciones editables
    // DRAFT	✅	Todo: marca, recursos corporativos, config
    // PENDING_REVIEW	❌	Solo lectura
    // APPROVED	Parcial	Solo PATCH /{id}/config (targeting + rewards)
    // REJECTED	❌	Solo lectura (mostrar adminNotes)
    // DESIGN_IN_PROGRESS	Parcial	Solo PATCH /{id}/config
    // CHANGES_REQUESTED	Parcial	Solo PATCH /{id}/config
    // PENDING_ADVERTISER_APPROVAL	❌	Solo botones de aprobar/rechazar diseño
    // READY_TO_LAUNCH	❌	Solo lectura
    // LAUNCHED	❌	Solo lectura
    @Override
    public void updateConfig(Long requestId, UpdateBrandingRequestConfigDTO dto, Long commercialId) {
        BrandingRequest request = findOwnedRequest(requestId, commercialId);

        if (!request.canBeUpdatedByCommercial()) {
            throw new ValidationException("Config cannot be updated from status: " + request.getStatus());
        }

        if (dto.getCategoryIds() != null) {
            List<Category> categories = categoryService.getValidatedCategories(dto.getCategoryIds());
            request.setCategories(categories);
        }

        if (dto.getMunicipalityCodes() != null && !dto.getMunicipalityCodes().isEmpty()) {
            List<Municipality> municipalities = targetingValidator.getValidatedMunicipalities(dto.getMunicipalityCodes());
            request.setTargetMunicipalities(municipalities);
        }

        if (dto.getMinAge() != null) request.setMinAge(dto.getMinAge());
        if (dto.getMaxAge() != null) request.setMaxAge(dto.getMaxAge());
        if (dto.getTargetGender() != null) request.setTargetGender(dto.getTargetGender());

        if (dto.getMinAge() != null && dto.getMaxAge() != null && dto.getMinAge() > dto.getMaxAge()) {
            throw new ValidationException("minAge cannot be greater than maxAge");
        }

        if (dto.getCampaignGoal() != null) request.setCampaignGoal(dto.getCampaignGoal());
        if (dto.getMaxSessionsPerUserPerDay() != null) request.setMaxSessionsPerUserPerDay(dto.getMaxSessionsPerUserPerDay());
        if (dto.getStartDate() != null) request.setStartDate(dto.getStartDate());

        log.info("BrandingRequest {} config updated by commercial user {}", requestId, commercialId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BrandingRequestSummaryDTO> getMyBrandingRequests(Long commercialUserId) {
        return brandingRequestRepository.findByCommercial_User_Id(commercialUserId)
            .stream().map(brandingMapper::toSummaryDTO).collect(Collectors.toList());
    }

    // ===== ADMIN =====

    @Override
    @Transactional(readOnly = true)
    public BrandingRequestDetailDTO getAdminBrandingRequestDetail(Long requestId) {
        BrandingRequest request = brandingRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("BrandingRequest not found: " + requestId));

        BrandingRequestDetailDTO detail = brandingMapper.toDetailDTO(request);

        List<CorporateResourceDTO> resources = request.getCorporateResources().stream()
            .map(resource -> {
                CorporateResourceDTO dto = brandingMapper.toCorporateResourceDTO(resource);
                if (resource.getStatus() == AssetStatus.VALIDATED) {
                    dto.setTemporalUrl(r2Service.getPrivateObject(resource.getObjectKey(), 1800));
                }
                return dto;
            })
            .collect(Collectors.toList());

        detail.setCorporateResources(resources);
        return detail;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GameDesignerSummaryDTO> getActiveDesigners() {
        return gameDesignerDetailsRepository.findByActiveTrue().stream()
            .map(d -> new GameDesignerSummaryDTO(
                d.getId(),
                d.getUser().getId(),
                d.getName(),
                d.getLastName(),
                d.getDesignerCode(),
                d.getCampaignsDesigned(),
                d.isCanPublishDirectly()
            ))
            .collect(Collectors.toList());
    }

    @Override
    public void assignDesigner(Long requestId, AssignDesignerDTO dto, Long adminUserId) {
        BrandingRequest request = brandingRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("BrandingRequest not found: " + requestId));

        GameDesignerDetails designer = gameDesignerDetailsRepository.findByUser_Id(dto.getDesignerUserId())
            .orElseThrow(() -> new EntityNotFoundException("Game designer not found for user: " + dto.getDesignerUserId()));

        if (!Boolean.TRUE.equals(designer.getActive())) {
            throw new IllegalStateException("Designer is not active");
        }

        request.setAssignedDesigner(designer);
        log.info("BrandingRequest {} assigned to designer user {} by admin {}", requestId, dto.getDesignerUserId(), adminUserId);
    }

    @Override
    public void approveBrandingRequest(Long requestId, ApproveBrandingRequestDTO dto, Long adminUserId) {
        BrandingRequest request = brandingRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("BrandingRequest not found: " + requestId));

        if (request.getStatus() != BrandingRequestStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Only PENDING_REVIEW requests can be approved");
        }

        AdminDetails admin = adminDetailsRepository.findById(adminUserId)
            .orElseThrow(() -> new EntityNotFoundException("Admin not found: " + adminUserId));

        GameDesignerDetails designer = gameDesignerDetailsRepository.findByUser_Id(dto.getDesignerUserId())
            .orElseThrow(() -> new EntityNotFoundException("Game designer not found for user: " + dto.getDesignerUserId()));

        request.setStatus(BrandingRequestStatus.APPROVED);
        request.setReviewedByAdmin(admin);
        request.setAssignedDesigner(designer);
        if (dto.getAdminNotes() != null) request.setAdminNotes(dto.getAdminNotes());

        log.info("BrandingRequest {} approved by admin {} and assigned to designer user {}",
            requestId, adminUserId, dto.getDesignerUserId());

        emailService.sendBrandingDesignerAssignedEmail(
            designer.getUser().getEmail(),
            designer.getName(),
            request.getBrandName(),
            request.getGame().getTitle(),
            dto.getAdminNotes());
        notificationService.createInternalNotification(
            designer.getUser().getId(),
            "Nuevo proyecto asignado",
            "Se te asignó el diseño para la marca \"" + request.getBrandName() + "\"",
            Instant.now());
    }

    @Override
    public void rejectBrandingRequest(Long requestId, RejectBrandingRequestDTO dto, Long adminUserId) {
        BrandingRequest request = brandingRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("BrandingRequest not found: " + requestId));

        if (request.getStatus() != BrandingRequestStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Only PENDING_REVIEW requests can be rejected");
        }

        AdminDetails admin = adminDetailsRepository.findById(adminUserId)
            .orElseThrow(() -> new EntityNotFoundException("Admin not found: " + adminUserId));

        request.setStatus(BrandingRequestStatus.REJECTED);
        request.setReviewedByAdmin(admin);
        request.setAdminNotes(dto.getAdminNotes());

        log.info("BrandingRequest {} rejected by admin {} with notes: {}", requestId, adminUserId, dto.getAdminNotes());

        emailService.sendBrandingRejectedEmail(
            request.getCommercial().getUser().getEmail(),
            request.getCommercial().getCompanyName(),
            request.getBrandName(),
            dto.getAdminNotes());
        notificationService.createInternalNotification(
            request.getCommercial().getUser().getId(),
            "Solicitud de branding no aprobada",
            "Tu solicitud para la marca \"" + request.getBrandName() + "\" no fue aprobada",
            Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BrandingRequestSummaryDTO> getAllBrandingRequests() {
        return brandingRequestRepository.findAll()
            .stream().map(brandingMapper::toSummaryDTO).collect(Collectors.toList());
    }

    // ===== REVISIÓN DEL ANUNCIANTE =====

    @Override
    public void approveDesign(Long requestId, Long userId) {
        BrandingRequest request = findOwnedRequest(requestId, userId);

        if (!request.canBeReviewedByAdvertiser()) {
            throw new IllegalStateException("Design cannot be approved from status: " + request.getStatus());
        }

        request.setStatus(BrandingRequestStatus.READY_TO_LAUNCH);
        log.info("BrandingRequest {} design approved by commercial user {} → READY_TO_LAUNCH", requestId, userId);

        emailService.sendBrandingReadyToLaunchEmail(
            adminNotificationEmail,
            request.getBrandName(),
            request.getGame().getTitle());
        if (request.getReviewedByAdmin() != null) {
            notificationService.createInternalNotification(
                request.getReviewedByAdmin().getUser().getId(),
                "Campaña lista para lanzar",
                "\"" + request.getBrandName() + "\" fue aprobada por el anunciante y está lista para lanzar",
                Instant.now());
        }
    }

    @Override
    public void requestDesignChanges(Long requestId, Long userId) {
        BrandingRequest request = findOwnedRequest(requestId, userId);

        if (!request.canBeReviewedByAdvertiser()) {
            throw new IllegalStateException("Design changes cannot be requested from status: " + request.getStatus());
        }

        request.setStatus(BrandingRequestStatus.CHANGES_REQUESTED);
        log.info("BrandingRequest {} → CHANGES_REQUESTED by commercial user {}", requestId, userId);

        emailService.sendBrandingChangesRequestedEmail(
            request.getAssignedDesigner().getUser().getEmail(),
            request.getAssignedDesigner().getName(),
            request.getBrandName(),
            null);
        notificationService.createInternalNotification(
            request.getAssignedDesigner().getUser().getId(),
            "Cambios solicitados en el diseño",
            "El anunciante pidió cambios en el diseño de \"" + request.getBrandName() + "\"",
            Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public String getPreviewUrl(Long requestId, Long userId) {
        BrandingRequest request = findOwnedRequest(requestId, userId);

        if (request.getGameConfig() == null || request.getGameConfig().isEmpty()) {
            throw new IllegalStateException("Design has not been submitted yet — no preview available");
        }

        return gameService.generatePreviewUrl(request);
    }

    // ===== RECURSOS CORPORATIVOS =====

    @Override
    public CorporateResourceUploadPermissionDTO generateResourceUploadUrl(Long requestId, FileUploadRequestDTO dto, Long userId) {
        BrandingRequest request = findOwnedRequest(requestId, userId);

        if (request.getStatus() != BrandingRequestStatus.DRAFT) {
            throw new IllegalStateException("Corporate resources can only be uploaded in DRAFT status");
        }

        String ext = extractExtension(dto.getOriginalFileName());
        String objectKey = String.format("branding/%d/resources/%s%s", requestId, UUID.randomUUID(), ext);

        FileUploadPermissionDTO permission = r2Service.generateUploadUrl(true, objectKey, dto.getContentType());
        String temporalUrl = r2Service.getPrivateObject(objectKey, 1800);

        CorporateResource resource = CorporateResource.builder()
            .brandingRequest(request)
            .objectKey(objectKey)
            .originalFileName(dto.getOriginalFileName())
            .sizeBytes(dto.getSizeBytes())
            .contentType(dto.getContentType())
            .uploadedBy(userId)
            .status(AssetStatus.PENDING)
            .build();

        CorporateResource saved = corporateResourceRepository.save(resource);
        log.info("CorporateResource {} created in PENDING for BrandingRequest {} by user {}", saved.getId(), requestId, userId);

        return CorporateResourceUploadPermissionDTO.builder()
            .resourceId(saved.getId())
            .temporalUrl(temporalUrl)
            .permission(permission)
            .build();
    }

    @Override
    public void confirmCorporateResource(Long requestId, ConfirmCorporateResourceDTO dto, Long userId) {
        findOwnedRequest(requestId, userId);

        CorporateResource resource = corporateResourceRepository
            .findByIdAndBrandingRequest_Id(dto.getResourceId(), requestId)
            .orElseThrow(() -> new EntityNotFoundException("Corporate resource not found: " + dto.getResourceId()));

        if (resource.getStatus() != AssetStatus.PENDING) {
            throw new IllegalStateException("Resource is not in PENDING status");
        }

        try {
            SupportedMimeType realMime = r2Service.validateUploadedObject(
                true,
                resource.getObjectKey(),
                resource.getSizeBytes(),
                MAX_RESOURCE_SIZE_BYTES,
                ALLOWED_RESOURCE_TYPES
            );

            if (realMime == null) {
                throw new IllegalStateException("Resource upload verification failed — file not found in storage");
            }

            resource.markAsValidated();
            corporateResourceRepository.save(resource);
            log.info("CorporateResource {} validated for BrandingRequest {}", resource.getId(), requestId);

        } catch (Exception e) {
            resource.markAsOrphan();
            corporateResourceRepository.save(resource);
            log.error("CorporateResource {} orphaned: {}", resource.getId(), e.getMessage());
            throw e;
        }
    }

    // ===== COMENTARIOS =====

    @Override
    @Transactional(readOnly = true)
    public List<BrandingRequestCommentDTO> getComments(Long requestId, Long userId) {
        findOwnedRequest(requestId, userId);
        return commentRepository.findByBrandingRequest_IdOrderByCreatedAtAsc(requestId)
                .stream()
                .map(this::toCommentDTO)
                .toList();
    }

    @Override
    public BrandingRequestCommentDTO addCommentAsCommercial(Long requestId, Long userId, AddCommentDTO dto) {
        BrandingRequest request = findOwnedRequest(requestId, userId);
        String authorName = request.getCommercial().getCompanyName();

        BrandingRequestComment comment = BrandingRequestComment.builder()
                .brandingRequest(request)
                .content(dto.getContent())
                .authorUserId(userId)
                .authorName(authorName)
                .authorRole(CommentAuthorRole.COMMERCIAL)
                .relatedStatus(request.getStatus())
                .build();

        BrandingRequestComment saved = commentRepository.save(comment);

        if (request.getAssignedDesigner() != null) {
            notificationService.createInternalNotification(
                    request.getAssignedDesigner().getUser().getId(),
                    "Nuevo mensaje en " + request.getBrandName(),
                    authorName + " comentó en tu proyecto",
                    Instant.now());
        }

        return toCommentDTO(saved);
    }

    private BrandingRequestCommentDTO toCommentDTO(BrandingRequestComment c) {
        return BrandingRequestCommentDTO.builder()
                .id(c.getId())
                .content(c.getContent())
                .authorName(c.getAuthorName())
                .authorRole(c.getAuthorRole())
                .relatedStatus(c.getRelatedStatus())
                .createdAt(c.getCreatedAt())
                .build();
    }

    // ===== HELPERS =====

    private BrandingRequest findOwnedRequest(Long requestId, Long userId) {
        return brandingRequestRepository.findByIdAndCommercialUserId(requestId, userId)
            .orElseThrow(() -> new EntityNotFoundException("BrandingRequest not found or not owned by user"));
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.'));
    }
}
