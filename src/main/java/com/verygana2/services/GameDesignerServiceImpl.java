package com.verygana2.services;

import java.util.Comparator;
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
import com.verygana2.dtos.branding.SubmitGameConfigDTO;
import com.verygana2.dtos.branding.UpdateDesignerNotesDTO;
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
import com.verygana2.services.interfaces.GameDesignerService;
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
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BrandingMapper brandingMapper;
    private final GameDesignerMapper gameDesignerMapper;
    private final AssetOrphanedService assetOrphanedService;
    private final R2Service r2Service;

    // ===== PERFIL =====

    @Override
    @Transactional(readOnly = true)
    public GameDesignerProfileResponseDTO getMyProfile(Long userId) {
        return gameDesignerMapper.toProfileDTO(findDetailsByUserId(userId));
    }

    @Override
    public void updateProfile(Long userId, UpdateGameDesignerProfileDTO dto) {
        GameDesignerDetails details = findDetailsByUserId(userId);
        if (dto.getName() != null && !dto.getName().isBlank()) details.setName(dto.getName());
        if (dto.getLastName() != null && !dto.getLastName().isBlank()) details.setLastName(dto.getLastName());
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

        FileUploadPermissionDTO permission = r2Service.generateUploadUrl(true, objectKey, request.getContentType());
        String temporalUrl = r2Service.getPrivateObject(objectKey, 2000);

        Asset asset = Asset.builder()
            .objectKey(objectKey)
            .mediaType(assetType)
            .mimeType(SupportedMimeType.fromValue(request.getContentType()))
            .sizeBytes(request.getSizeBytes())
            .status(AssetStatus.PENDING)
            .uploadedBy(userId)
            .build();

        asset = assetRepository.save(asset);
        log.info("Asset record {} created in PENDING for designer user {}", asset.getId(), userId);

        return AssetUploadPermissionDTO.builder()
            .assetId(asset.getId())
            .temporalUrl(temporalUrl)
            .publicUrl("https://cdn.verygana.com/public/" + objectKey)
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
                true, asset.getObjectKey(), asset.getSizeBytes(), maxSizeBytes, allowedTypes);

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
    }

    // ===== FLUJO DE BRANDING =====

    @Override
    public void saveGameConfig(Long requestId, Long userId, SubmitGameConfigDTO dto) {
        BrandingRequest request = findAssignedRequest(requestId, userId);

        if (!request.canBeUpdatedByDesigner()) {
            throw new IllegalStateException("Game config cannot be saved from status: " + request.getStatus());
        }

        // Auto-transición al iniciar el trabajo
        if (request.getStatus() == BrandingRequestStatus.APPROVED) {
            request.setStatus(BrandingRequestStatus.DESIGN_IN_PROGRESS);
            log.info("BrandingRequest {} transitioned to DESIGN_IN_PROGRESS by designer user {}", requestId, userId);
        }

        request.setGameConfig(dto.getConfig());
        brandingRequestRepository.save(request);
        log.info("Game config saved for BrandingRequest {} by designer user {}", requestId, userId);
    }

    @Override
    public void saveDraftFormData(Long requestId, Long userId, Map<String, Object> formData) {
        BrandingRequest request = findAssignedRequest(requestId, userId);
        if (!request.canBeUpdatedByDesigner()) {
            throw new IllegalStateException("Draft form data cannot be saved from status: " + request.getStatus());
        }
        request.setDraftFormData(formData);
        brandingRequestRepository.save(request);
        log.info("Draft form data saved for BrandingRequest {} by designer user {}", requestId, userId);
    }

    @Override
    public void updateDesignerNotes(Long requestId, Long userId, UpdateDesignerNotesDTO dto) {
        BrandingRequest request = findAssignedRequest(requestId, userId);
        if (!request.canBeUpdatedByDesigner()) {
            throw new IllegalStateException("Notes cannot be updated from status: " + request.getStatus());
        }
        request.setDesignerNotes(dto.getNotes());
        brandingRequestRepository.save(request);
        log.info("Designer notes updated for BrandingRequest {} by user {}", requestId, userId);
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

        request.setGameConfig(request.getDraftFormData());
        request.setStatus(BrandingRequestStatus.PENDING_ADVERTISER_APPROVAL);
        log.info("BrandingRequest {} submitted for advertiser review by designer user {}", requestId, userId);
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
