package com.verygana2.services.gameDesigner;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.branding.BrandingRequestSummaryDTO;
import com.verygana2.dtos.branding.CorporateResourceDTO;
import com.verygana2.dtos.branding.DesignerBrandingDetailDTO;
import com.verygana2.dtos.game.campaign.AssetConfirmRequest;
import com.verygana2.dtos.game.campaign.AssetUploadPermissionDTO;
import com.verygana2.dtos.game.campaign.GameSchemaResponse;
import com.verygana2.dtos.user.gamedesigner.ChangePasswordDTO;
import com.verygana2.dtos.user.gamedesigner.GameDesignerProfileResponseDTO;
import com.verygana2.dtos.user.gamedesigner.ResetPasswordByEmailDTO;
import com.verygana2.dtos.user.gamedesigner.UpdateGameDesignerProfileDTO;
import com.verygana2.mappers.BrandingMapper;
import com.verygana2.mappers.GameDesignerMapper;
import com.verygana2.models.User;
import com.verygana2.models.branding.Asset;
import com.verygana2.models.branding.BrandingRequest;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.BrandingRequestStatus;
import com.verygana2.models.enums.MediaType;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameConfigDefinition;
import com.verygana2.models.userDetails.GameDesignerDetails;
import com.verygana2.repositories.UserRepository;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.repositories.details.GameDesignerDetailsRepository;
import com.verygana2.repositories.games.AssetRepository;
import com.verygana2.dtos.branding.AddCommentDTO;
import com.verygana2.dtos.branding.BrandingRequestCommentDTO;
import com.verygana2.models.branding.BrandingRequestComment;
import com.verygana2.models.enums.CommentAuthorRole;
import com.verygana2.repositories.branding.BrandingRequestCommentRepository;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.GameDesignerService;
import com.verygana2.services.interfaces.GameService;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.storage.service.AssetOrphanedService;
import com.verygana2.storage.service.R2Service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class GameDesignerServiceImpl implements GameDesignerService {

    private final GameDesignerDetailsRepository designerDetailsRepository;
    private final BrandingRequestRepository brandingRequestRepository;
    private final BrandingRequestCommentRepository commentRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BrandingMapper brandingMapper;
    private final GameDesignerMapper gameDesignerMapper;
    private final AssetOrphanedService assetOrphanedService;
    private final R2Service r2Service;
    private final GameService gameService;
    private final EmailService emailService;
    private final NotificationService notificationService;

    // ===== PERFIL =====

    @Override
    @Transactional(readOnly = true)
    public GameDesignerProfileResponseDTO getMyProfile(Long userId) {
        return gameDesignerMapper.toProfileDTO(findDetailsByUserId(userId));
    }

    @Override
    public void updateProfile(Long userId, UpdateGameDesignerProfileDTO dto) {
        GameDesignerDetails details = findDetailsByUserId(userId);
        if (dto.getBio() != null) details.setBio(dto.getBio());
        designerDetailsRepository.save(details);
        log.info("Profile updated for game designer user {}", userId);
    }

    // ===== CONTRASEÑA =====

    @Override
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for game designer user {}", userId);
    }

    @Override
    public void resetPasswordByDesignerCode(ResetPasswordByEmailDTO dto) {
        User user = userRepository.findByEmail(dto.getEmail())
            .orElseThrow(() -> new EntityNotFoundException("No account found for that email"));
        GameDesignerDetails details = designerDetailsRepository.findByUser_Id(user.getId())
            .orElseThrow(() -> new IllegalArgumentException("Account is not a game designer"));
        if (!details.getDesignerCode().equalsIgnoreCase(dto.getDesignerCode())) {
            throw new IllegalArgumentException("Designer code does not match");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
        log.info("Password reset via designer code for user {}", user.getId());
    }

    // ===== SOLICITUDES ASIGNADAS =====

    @Override
    @Transactional(readOnly = true)
    public List<BrandingRequestSummaryDTO> getAssignedBrandingRequests(Long userId) {
        return brandingRequestRepository.findByAssignedDesigner_User_Id(userId)
            .stream()
            .map(brandingMapper::toSummaryDTO)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DesignerBrandingDetailDTO getAssignedBrandingRequestDetail(Long requestId, Long userId) {
        BrandingRequest request = findAssignedRequest(requestId, userId);
        return toDesignerDetailDTO(request);
    }

    // ===== ASSETS DEL JUEGO (RJSF) =====

    @Override
    public AssetUploadPermissionDTO generateUploadUrl(FileUploadRequestDTO request, Long userId) {
        log.info("Generating asset upload URL for designer user {}: {}", userId, request.getOriginalFileName());

        MediaType assetType = MediaType.fromMimeType(request.getContentType());
        String objectKey = String.format("campaigns/designer-%s/%s/%s",
            userId, assetType.getValue(), UUID.randomUUID());

        FileUploadPermissionDTO permission = r2Service.generateUploadUrl(false, objectKey, request.getContentType());
        String publicUrl = r2Service.buildPublicUrl(objectKey);

        Asset.AssetBuilder assetBuilder = Asset.builder()
            .objectKey(objectKey)
            .mediaType(assetType)
            .mimeType(SupportedMimeType.fromValue(request.getContentType()))
            .sizeBytes(request.getSizeBytes())
            .status(AssetStatus.PENDING)
            .uploadedBy(userId);

        if (request.getBrandingRequestId() != null) {
            BrandingRequest brandingRequest = brandingRequestRepository
                .findByIdAndAssignedDesigner_User_Id(request.getBrandingRequestId(), userId)
                .orElseThrow(() -> new EntityNotFoundException(
                    "Branding request not found or not assigned to this designer"));
            assetBuilder.brandingRequest(brandingRequest);
        }

        Asset asset = assetRepository.save(assetBuilder.build());
        log.info("Asset record {} created in PENDING for designer user {}", asset.getId(), userId);

        return AssetUploadPermissionDTO.builder()
            .assetId(asset.getId())
            .publicUrl(publicUrl)
            .temporalUrl(publicUrl)
            .permission(permission)
            .build();
    }

    @Override
    public void confirmUpload(AssetConfirmRequest request) {
        log.info("Confirming asset upload for asset {}", request.getAssetId());

        Asset asset = assetRepository.findById(request.getAssetId())
            .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + request.getAssetId()));

        if (asset.getStatus() != AssetStatus.PENDING) {
            throw new IllegalStateException("Asset is not in PENDING status");
        }

        try {
            long maxSizeBytes = 1024 * 1024 * 10L;
            Set<SupportedMimeType> allowedTypes = SupportedMimeType.getSupportedMimeTypesForMediaType(asset.getMediaType());

            SupportedMimeType realMime = r2Service.validateUploadedObject(
                false, asset.getObjectKey(), asset.getSizeBytes(), maxSizeBytes, allowedTypes);

            if (realMime == null) {
                throw new IllegalStateException("Asset upload verification failed");
            }

            asset.setStatus(AssetStatus.VALIDATED);
            asset.setMimeType(realMime);
            assetRepository.save(asset);
            log.info("Asset {} validated for designer", asset.getId());

        } catch (Exception e) {
            log.error("Asset {} orphaned: {}", request.getAssetId(), e.getMessage());
            assetOrphanedService.markAsOrphaned(request.getAssetId());
            throw e;
        }

        if (request.getPreviousAssetId() != null) {
            assetRepository.findById(request.getPreviousAssetId()).ifPresent(previous -> {
                try {
                    r2Service.deletePublicObject(previous.getObjectKey());
                } catch (Exception e) {
                    log.warn("Failed to delete previous asset {} from R2: {}", previous.getId(), e.getMessage());
                }
                previous.setStatus(AssetStatus.DELETED);
                assetRepository.save(previous);
                log.info("Previous asset {} replaced and marked DELETED", previous.getId());
            });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String getPreviewUrl(Long requestId, Long userId) {
        BrandingRequest request = findAssignedRequest(requestId, userId);
        return gameService.generatePreviewUrl(request);
    }

    @Override
    public void deleteAsset(Long assetId, Long userId) {
        Asset asset = assetRepository.findById(assetId)
            .orElseThrow(() -> new EntityNotFoundException("Asset not found: " + assetId));

        if (!userId.equals(asset.getUploadedBy())) {
            throw new SecurityException("Asset does not belong to this designer");
        }

        if (asset.getStatus() == AssetStatus.DELETED) {
            throw new IllegalStateException("Asset is already deleted");
        }

        try {
            r2Service.deletePublicObject(asset.getObjectKey());
        } catch (Exception e) {
            log.warn("Failed to delete asset {} from R2: {}", assetId, e.getMessage());
        }

        asset.setStatus(AssetStatus.DELETED);
        assetRepository.save(asset);
        log.info("Asset {} deleted by designer user {}", assetId, userId);
    }

    // ===== FLUJO DE BRANDING =====

    @Override
    public void saveDraftFormData(Long requestId, Long userId, Map<String, Object> formData) {
        BrandingRequest request = findAssignedRequest(requestId, userId);
        if (!request.canBeUpdatedByDesigner()) {
            throw new IllegalStateException("Draft form data cannot be saved from status: " + request.getStatus());
        }
        if (request.getStatus() == BrandingRequestStatus.APPROVED) {
            request.setStatus(BrandingRequestStatus.DESIGN_IN_PROGRESS);
            log.info("BrandingRequest {} transitioned to DESIGN_IN_PROGRESS by designer user {}", requestId, userId);
        }
        request.setDraftFormData(formData);
        brandingRequestRepository.save(request);
        log.info("Draft form data saved for BrandingRequest {} by designer user {}", requestId, userId);
    }


    @Override
    public void submitDesignForReview(Long requestId, Long userId) {
        BrandingRequest request = findAssignedRequest(requestId, userId);

        if (!request.canSubmitDesignForReview()) {
            throw new IllegalStateException("Design cannot be submitted for review from status: " + request.getStatus());
        }

        if (request.getDraftFormData() == null || request.getDraftFormData().isEmpty()) {
            throw new IllegalStateException("The form must have saved data before submitting for review");
        }

        request.setGameConfig(stripAssetMetadata(request.getDraftFormData()));
        request.setStatus(BrandingRequestStatus.PENDING_ADVERTISER_APPROVAL);
        log.info("BrandingRequest {} submitted for advertiser review by designer user {}", requestId, userId);

        emailService.sendBrandingDesignSubmittedEmail(
            request.getCommercial().getUser().getEmail(),
            request.getCommercial().getCompanyName(),
            request.getBrandName(),
            request.getGame().getTitle());
        notificationService.createInternalNotification(
            request.getCommercial().getUser().getId(),
            "Diseño listo para revisar",
            "El diseñador entregó el juego para \"" + request.getBrandName() + "\" — entra a revisarlo",
            Instant.now());
    }

    // ===== HELPERS =====

    private Map<String, Object> stripAssetMetadata(Map<String, Object> formData) {
        Map<String, Object> cleaned = new LinkedHashMap<>();
        formData.forEach((k, v) -> cleaned.put(k, stripValue(v)));
        return cleaned;
    }

    @SuppressWarnings("unchecked")
    private Object stripValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> m = (Map<String, Object>) map;
            if (m.containsKey("assetId") && m.containsKey("url")) {
                return m.get("url");
            }
            Map<String, Object> cleaned = new LinkedHashMap<>();
            m.forEach((k, v) -> cleaned.put(k, stripValue(v)));
            return cleaned;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::stripValue).toList();
        }
        return value;
    }

    // ===== COMENTARIOS =====

    @Override
    @Transactional(readOnly = true)
    public List<BrandingRequestCommentDTO> getComments(Long requestId, Long userId) {
        findAssignedRequest(requestId, userId);
        return commentRepository.findByBrandingRequest_IdOrderByCreatedAtAsc(requestId)
                .stream()
                .map(this::toCommentDTO)
                .toList();
    }

    @Override
    public BrandingRequestCommentDTO addCommentAsDesigner(Long requestId, Long userId, AddCommentDTO dto) {
        BrandingRequest request = findAssignedRequest(requestId, userId);
        GameDesignerDetails designer = findDetailsByUserId(userId);
        String authorName = designer.getName() + " " + designer.getLastName();

        BrandingRequestComment comment = BrandingRequestComment.builder()
                .brandingRequest(request)
                .content(dto.getContent())
                .authorUserId(userId)
                .authorName(authorName)
                .authorRole(CommentAuthorRole.DESIGNER)
                .relatedStatus(request.getStatus())
                .build();

        BrandingRequestComment saved = commentRepository.save(comment);

        notificationService.createInternalNotification(
                request.getCommercial().getUser().getId(),
                "Nuevo mensaje en " + request.getBrandName(),
                "El diseñador comentó en tu solicitud",
                Instant.now());

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

    private BrandingRequest findAssignedRequest(Long requestId, Long userId) {
        return brandingRequestRepository.findByIdAndAssignedDesigner_User_Id(requestId, userId)
            .orElseThrow(() -> new EntityNotFoundException("Branding request not found or not assigned to this designer"));
    }

    private GameDesignerDetails findDetailsByUserId(Long userId) {
        return designerDetailsRepository.findByUser_Id(userId)
            .orElseThrow(() -> new EntityNotFoundException("Game designer profile not found for user: " + userId));
    }

    private DesignerBrandingDetailDTO toDesignerDetailDTO(BrandingRequest request) {
        DesignerBrandingDetailDTO dto = gameDesignerMapper.toDesignerDetailDTO(request);

        dto.setCorporateResources(request.getCorporateResources().stream()
            .map(resource -> {
                CorporateResourceDTO resourceDto = brandingMapper.toCorporateResourceDTO(resource);
                if (resource.getStatus() == AssetStatus.VALIDATED) {
                    resourceDto.setTemporalUrl(r2Service.getPrivateObject(resource.getObjectKey(), 1800));
                }
                return resourceDto;
            })
            .collect(Collectors.toList()));

        dto.setGameSchema(buildGameSchema(request.getGame()));
        dto.setDraftFormData(request.getDraftFormData());

        return dto;
    }

    private GameSchemaResponse buildGameSchema(Game game) {
        GameConfigDefinition config = game.getConfigDefinitions().stream()
            .max(Comparator.comparing(GameConfigDefinition::getVersion))
            .orElseThrow(() -> new ValidationException("Game has no config definition"));

        return new GameSchemaResponse(
            game.getId(),
            game.getTitle(),
            config.getVersion().toString(),
            config.getJsonSchema(),
            config.getUiSchema()
        );
    }
}
