package com.verygana2.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.branding.ApproveBrandingRequestDTO;
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
import com.verygana2.dtos.branding.RequestDesignChangesDTO;
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
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.GameDesignerDetails;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.repositories.branding.CorporateResourceRepository;
import com.verygana2.repositories.details.AdminDetailsRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.details.GameDesignerDetailsRepository;
import com.verygana2.repositories.games.GameRepository;
import com.verygana2.services.interfaces.BrandingRequestService;
import com.verygana2.services.interfaces.CategoryService;
import com.verygana2.storage.service.R2Service;
import com.verygana2.utils.validators.TargetingValidator;

import jakarta.persistence.EntityNotFoundException;
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

    private final BrandingRequestRepository brandingRequestRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final GameRepository gameRepository;
    private final AdminDetailsRepository adminDetailsRepository;
    private final GameDesignerDetailsRepository gameDesignerDetailsRepository;
    private final CorporateResourceRepository corporateResourceRepository;
    private final CategoryService categoryService;
    private final TargetingValidator targetingValidator;
    private final R2Service r2Service;
    private final BrandingMapper brandingMapper;

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
    public void submitForReview(Long requestId, Long userId) {
        BrandingRequest request = findOwnedRequest(requestId, userId);

        if (!request.canBeSubmitted()) {
            throw new IllegalStateException("Request cannot be submitted from status: " + request.getStatus());
        }

        long validatedResources = request.getCorporateResources().stream()
            .filter(r -> r.getStatus() == AssetStatus.VALIDATED)
            .count();

        if (validatedResources == 0) {
            throw new IllegalStateException("At least one corporate resource must be uploaded and confirmed before submitting");
        }

        request.setStatus(BrandingRequestStatus.PENDING_REVIEW);
        log.info("BrandingRequest {} submitted for review by commercial user {}", requestId, userId);
    }

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
            throw new IllegalStateException("Config cannot be updated from status: " + request.getStatus());
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
            throw new IllegalArgumentException("minAge cannot be greater than maxAge");
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
    }

    @Override
    public void requestDesignChanges(Long requestId, Long userId, RequestDesignChangesDTO dto) {
        BrandingRequest request = findOwnedRequest(requestId, userId);

        if (!request.canBeReviewedByAdvertiser()) {
            throw new IllegalStateException("Design changes cannot be requested from status: " + request.getStatus());
        }

        request.setStatus(BrandingRequestStatus.CHANGES_REQUESTED);
        request.setDesignerNotes(dto.getDesignerNotes());
        log.info("BrandingRequest {} design changes requested by commercial user {} → CHANGES_REQUESTED", requestId, userId);
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
