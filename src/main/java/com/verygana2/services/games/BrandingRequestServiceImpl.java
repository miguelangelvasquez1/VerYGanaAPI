package com.verygana2.services.games;

import java.time.Instant;
import java.util.Collections;
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
import com.verygana2.models.TargetAudience;
import com.verygana2.models.branding.BrandingRequest;
import com.verygana2.models.branding.Campaign;
import com.verygana2.models.branding.CorporateResource;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.BrandingRequestStatus;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.RequirePlanCapability;
import com.verygana2.models.finance.plans.RequirePlanCapability.Capability;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameConfigDefinition;
import com.verygana2.models.branding.BrandingRequestComment;
import com.verygana2.models.enums.CommentAuthorRole;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.GameDesignerDetails;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.branding.BrandingRequestCommentRepository;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.repositories.branding.CorporateResourceRepository;
import com.verygana2.repositories.details.AdminDetailsRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.details.GameDesignerDetailsRepository;
import com.verygana2.repositories.games.CampaignRepository;
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
    private final CampaignRepository campaignRepository;
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
    private final WalletRepository walletRepository;

    // ===== CATÁLOGO DE JUEGOS =====

    @Override
    @Transactional(readOnly = true)
    public Page<BrandingGameDTO> getGamesForBranding(Pageable pageable) {
        return gameRepository.findByActiveTrue(pageable).map(brandingMapper::toBrandingGameDTO);
    }

    // ===== ANUNCIANTE =====

    @Override
    @Transactional(readOnly = true)
    public List<BrandingRequestSummaryDTO> getMyBrandingRequests(Long commercialUserId) {
        return brandingRequestRepository.findByCommercial_User_Id(commercialUserId)
            .stream().map(request -> {
                int count = (int) request.getCorporateResources().stream()
                    .filter(r -> r.getStatus() == AssetStatus.VALIDATED)
                    .count();
                BrandingRequestSummaryDTO dto = brandingMapper.toSummaryDTO(request);
                dto.setCorporateResourceCount(count);
                return dto;
            }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BrandingRequestDetailDTO getBrandingRequestDetail(Long requestId, Long userId) {
        BrandingRequest request = findOwnedRequest(requestId, userId);

        boolean completeTargeting = request.hasCompleteTargeting();

        BrandingRequestDetailDTO detail = brandingMapper.toDetailDTO(request);
        detail.setHasCompleteTargeting(completeTargeting);

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
    @RequirePlanCapability(value = {Capability.CAN_USE_GAMES, Capability.MAX_BRANDED_GAMES}, commercialIdParam = "commercialUserId")
    public BrandingRequestSummaryDTO createBrandingRequest(CreateBrandingRequestDTO dto, Long commercialUserId) {
        CommercialDetails commercial = commercialDetailsRepository.findByUser_Id(commercialUserId)
            .orElseThrow(() -> new EntityNotFoundException("Commercial profile not found for user: " + commercialUserId));

        if (dto.getBudgetCents() > commercial.getWallet().getBalanceCents()) {
            throw new ValidationException("Insufficient funds in wallet.");
        }

        Wallet wallet = walletRepository.findByCommercialId(commercialUserId).orElseThrow(() -> new EntityNotFoundException("Wallet del anunciante no encontrado"));
 
        wallet.consume(dto.getBudgetCents());
        walletRepository.save(wallet);

        Game game = gameRepository.findById(dto.getGameId())
            .orElseThrow(() -> new EntityNotFoundException("Game not found: " + dto.getGameId()));

        GameConfigDefinition latestConfig = game.getConfigDefinitions() == null ? null
            : game.getConfigDefinitions().stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsLatest()))
                .findFirst()
                .orElse(null);

        if (latestConfig == null) {
            throw new ValidationException("Game has no active config definition: " + game.getId());
        }

        BrandingRequest request = BrandingRequest.builder()
            .commercial(commercial)
            .game(game)
            .gameConfigDefinition(latestConfig)
            .brandName(dto.getBrandName())
            .brandDescription(dto.getBrandDescription())
            .targetUrl(dto.getTargetUrl())
            .budgetCents(dto.getBudgetCents())
            .campaignGoal(dto.getCampaignGoal())
            .status(BrandingRequestStatus.DRAFT)
            .build();

        BrandingRequest saved = brandingRequestRepository.save(request);
        log.info("BrandingRequest {} created in DRAFT by commercial user {}", saved.getId(), commercialUserId);
        return brandingMapper.toSummaryDTO(saved);
    }

    @Override
    @RequirePlanCapability(value = {Capability.CAN_USE_GAMES}, commercialIdParam = "userId")
    public void submitForReview(Long requestId, Long userId, String notes) {
        BrandingRequest request = findOwnedRequest(requestId, userId);

        if (!request.canBeSubmitted()) {
            throw new ValidationException("Request cannot be submitted from status: " + request.getStatus());
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
    // CAMPAIGN_CREATED	❌	Solo lectura
    @Override
    @RequirePlanCapability(value = {Capability.CAN_USE_GAMES}, commercialIdParam = "commercialId")
    public void updateConfig(Long requestId, UpdateBrandingRequestConfigDTO dto, Long commercialId) {
        BrandingRequest request = findOwnedRequest(requestId, commercialId);

        if (!request.canBeUpdatedByCommercial()) {
            throw new ValidationException("Config cannot be updated from status: " + request.getStatus());
        }

        TargetAudience ta = request.getTargetAudience();
        if (ta == null) {
            ta = new TargetAudience();
            request.setTargetAudience(ta);
        }

        if (dto.getCategoryIds() != null) {
            ta.setCategories(categoryService.getValidatedCategories(dto.getCategoryIds()));
        }

        if (dto.getMunicipalityCodes() != null) {
            ta.setTargetMunicipalities(dto.getMunicipalityCodes().isEmpty()
                    ? Collections.emptyList()
                    : targetingValidator.getValidatedMunicipalities(dto.getMunicipalityCodes()));
        }

        if (dto.getMinAge() != null) ta.setMinAge(dto.getMinAge());
        if (dto.getMaxAge() != null) ta.setMaxAge(dto.getMaxAge());
        if (dto.getTargetGender() != null) ta.setTargetGender(dto.getTargetGender());

        if (dto.getMinAge() != null && dto.getMaxAge() != null && dto.getMinAge() > dto.getMaxAge()) {
            throw new ValidationException("minAge cannot be greater than maxAge");
        }

        if (dto.getMaxSessionsPerUserPerDay() != null) request.setMaxSessionsPerUserPerDay(dto.getMaxSessionsPerUserPerDay());
        if (dto.getStartDate() != null) request.setStartDate(dto.getStartDate());

        log.info("BrandingRequest {} config updated by commercial user {}", requestId, commercialId);
    }

    // ===== ADMIN =====

    @Override
    @Transactional(readOnly = true)
    public BrandingRequestDetailDTO getAdminBrandingRequestDetail(Long requestId) {
        BrandingRequest request = brandingRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("BrandingRequest not found: " + requestId));

        boolean completeTargeting = request.hasCompleteTargeting();
        BrandingRequestDetailDTO detail = brandingMapper.toDetailDTO(request);
        detail.setHasCompleteTargeting(completeTargeting);

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
                d.getCampaignsDesigned()
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
            throw new ValidationException("Designer is not active");
        }

        request.setAssignedDesigner(designer);
        log.info("BrandingRequest {} assigned to designer user {} by admin {}", requestId, dto.getDesignerUserId(), adminUserId);
    }

    @Override
    public void approveBrandingRequest(Long requestId, ApproveBrandingRequestDTO dto, Long adminUserId) {
        BrandingRequest request = brandingRequestRepository.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("BrandingRequest not found: " + requestId));

        if (request.getStatus() != BrandingRequestStatus.PENDING_REVIEW) {
            throw new ValidationException("Only PENDING_REVIEW requests can be approved");
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
            throw new ValidationException("Only PENDING_REVIEW requests can be rejected");
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
    @RequirePlanCapability(value = {Capability.CAN_USE_GAMES}, commercialIdParam = "userId")
    public void approveDesign(Long requestId, Long userId) {
        BrandingRequest request = findOwnedRequest(requestId, userId);

        if (!request.canBeReviewedByAdvertiser()) {
            throw new ValidationException("Design cannot be approved from status: " + request.getStatus());
        }

        if (request.getGameConfig() == null || request.getGameConfig().isEmpty()) {
            throw new IllegalStateException("La configuración del juego no está completa");
        }

        Campaign campaign = campaignRepository.save(brandingMapper.toCampaign(request));

        request.setStatus(BrandingRequestStatus.CAMPAIGN_CREATED);
        request.setCampaign(campaign);

        log.info("BrandingRequest {} aprobada → Campaign {} creada y activa, commercial user {}",
            requestId, campaign.getId(), userId);

        notifyDesignApproved(request, userId);
    }

    @Override
    @RequirePlanCapability(value = {Capability.CAN_USE_GAMES}, commercialIdParam = "userId")
    public void requestDesignChanges(Long requestId, Long userId) {
        BrandingRequest request = findOwnedRequest(requestId, userId);

        if (!request.canBeReviewedByAdvertiser()) {
            throw new ValidationException("Design changes cannot be requested from status: " + request.getStatus());
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
            throw new ValidationException("Design has not been submitted yet — no preview available");
        }

        return gameService.generatePreviewUrl(request);
    }

    // ===== RECURSOS CORPORATIVOS =====

    @Override
    @RequirePlanCapability(value = {Capability.CAN_USE_GAMES}, commercialIdParam = "userId")
    public CorporateResourceUploadPermissionDTO generateResourceUploadUrl(Long requestId, FileUploadRequestDTO dto, Long userId) {
        BrandingRequest request = findOwnedRequest(requestId, userId);

        if (Set.of(BrandingRequestStatus.CAMPAIGN_CREATED,
                   BrandingRequestStatus.REJECTED, BrandingRequestStatus.CANCELLED)
                .contains(request.getStatus())) {
            throw new ValidationException("Corporate resources cannot be uploaded in status: " + request.getStatus());
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
    @RequirePlanCapability(value = {Capability.CAN_USE_GAMES}, commercialIdParam = "userId")
    public void confirmCorporateResource(Long requestId, ConfirmCorporateResourceDTO dto, Long userId) {
        findOwnedRequest(requestId, userId);

        CorporateResource resource = corporateResourceRepository
            .findByIdAndBrandingRequest_Id(dto.getResourceId(), requestId)
            .orElseThrow(() -> new EntityNotFoundException("Corporate resource not found: " + dto.getResourceId()));

        if (resource.getStatus() != AssetStatus.PENDING) {
            throw new ValidationException("Resource is not in PENDING status");
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
                throw new ValidationException("Resource upload verification failed — file not found in storage");
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
    @RequirePlanCapability(value = {Capability.CAN_USE_GAMES}, commercialIdParam = "userId")
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

        private void notifyDesignApproved(BrandingRequest request, Long userId) {
        emailService.sendBrandingReadyToLaunchEmail(
            adminNotificationEmail,
            request.getBrandName(),
            request.getGame().getTitle());

        notificationService.createInternalNotification(
            userId,
            "¡Diseño de campaña aprobado!",
            "El diseño para \"" + request.getBrandName() + "\" fue aprobado por el anunciante",
            Instant.now());

        if (request.getReviewedByAdmin() != null) {
            notificationService.createInternalNotification(
                request.getReviewedByAdmin().getUser().getId(),
                "Diseño de campaña aprobado",
                "\"" + request.getBrandName() + "\" fue aprobado por el anunciante",
                Instant.now());
        }
    }
}
