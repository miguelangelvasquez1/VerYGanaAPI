package com.verygana2.services.pet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.branding.GameDesignerSummaryDTO;
import com.verygana2.dtos.pet.ApprovePetRequestDTO;
import com.verygana2.dtos.pet.CatalogIntegrationRequestDTO;
import com.verygana2.dtos.pet.CatalogIntegrationResponseDTO;
import com.verygana2.dtos.pet.PetCatalogItemRequestDTO;
import com.verygana2.dtos.pet.PetImageUploadPermissionDTO;
import com.verygana2.dtos.pet.PetImageUploadRequestDTO;
import com.verygana2.models.enums.CatalogRequestStatus;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.finance.plans.RequirePlanCapability;
import com.verygana2.models.pets.CatalogIntegrationRequest;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.GameDesignerDetails;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.details.GameDesignerDetailsRepository;
import com.verygana2.repositories.pet.CatalogIntegrationRequestRepository;
import com.verygana2.services.interfaces.pet.CatalogIntegrationRequestService;
import com.verygana2.dtos.pet.CatalogRequestCommentDTO;
import com.verygana2.models.enums.CommentAuthorRole;
import com.verygana2.models.pets.CatalogRequestComment;
import com.verygana2.repositories.pet.CatalogRequestCommentRepository;
import com.verygana2.utils.validators.pet.PetSchemaValidator;
import com.verygana2.utils.validators.games.ValidationPipeline.ValidationError;
import com.verygana2.services.interfaces.pet.PetCatalogService;
import com.verygana2.storage.service.R2Service;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogIntegrationRequestServiceImpl implements CatalogIntegrationRequestService {

    /** Carpeta dentro del bucket de mascotas donde viven las imágenes de las solicitudes. */
    private static final String IMAGE_KEY_PREFIX = "catalog-requests/";

    /** Clave del borrador que guarda el sprite; no viaja en PetCatalogItemRequestDTO. */
    private static final String DRAFT_SPRITE_KEY = "spriteObjectKey";

    /** Solo imágenes: el diseñador necesita ver el producto para convertirlo en sprite. */
    private static final Set<SupportedMimeType> ALLOWED_IMAGE_TYPES = Set.of(
            SupportedMimeType.IMAGE_PNG,
            SupportedMimeType.IMAGE_JPEG,
            SupportedMimeType.IMAGE_JPG,
            SupportedMimeType.IMAGE_WEBP);

    @Value("${cloudflare.r2.pets-cdn-domain:}")
    private String petsCdnDomain;

    @Value("${cloudflare.r2.pets-bucket-name:verygana-pets}")
    private String petsBucketName;

    @Value("${pets.catalog-request-image.max-size-bytes:5242880}")
    private long maxImageSizeBytes;

    private final CatalogIntegrationRequestRepository requestRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final GameDesignerDetailsRepository designerDetailsRepository;
    private final PetCatalogService catalogService;
    private final R2Service r2Service;
    private final ObjectMapper objectMapper;
    private final CatalogRequestCommentRepository commentRepository;
    private final PetSchemaValidator schemaValidator;

    @Override
    @RequirePlanCapability(value = RequirePlanCapability.Capability.CAN_HAVE_PETS, commercialIdParam = "userId")
    public PetImageUploadPermissionDTO prepareImageUpload(Long userId, PetImageUploadRequestDTO dto) {
        CommercialDetails commercial = commercialDetailsRepository.findByUser_Id(userId)
                .orElseThrow(() -> new EntityNotFoundException("Commercial not found for userId=" + userId));

        if (dto.sizeBytes() > maxImageSizeBytes) {
            throw new ValidationException(
                    "Imagen muy grande. Máximo permitido: " + (maxImageSizeBytes / 1024 / 1024) + " MB");
        }

        SupportedMimeType declaredMime;
        try {
            declaredMime = SupportedMimeType.fromValue(dto.contentType());
        } catch (ValidationException e) {
            throw new ValidationException("Formato no soportado. Use PNG, JPEG o WEBP.");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(declaredMime)) {
            throw new ValidationException("Formato no soportado. Use PNG, JPEG o WEBP.");
        }

        // El id del comercial va en la clave: es lo que permite comprobar en submit()
        // que quien manda la clave es el mismo que la pidió.
        String objectKey = IMAGE_KEY_PREFIX + commercial.getId() + "/" + UUID.randomUUID();

        FileUploadPermissionDTO permission =
                r2Service.generateUploadUrlInBucket(petsBucketName, objectKey, declaredMime.getMime());

        log.info("Permiso de subida de imagen de catálogo generado para comercial {} → {}",
                commercial.getId(), objectKey);

        return new PetImageUploadPermissionDTO(
                objectKey, permission.getUploadUrl(), permission.getExpiresInSeconds());
    }

    @Override
    @RequirePlanCapability(value = RequirePlanCapability.Capability.CAN_HAVE_PETS, commercialIdParam = "userId", requiresBudget = true)
    public CatalogIntegrationResponseDTO submit(Long userId, CatalogIntegrationRequestDTO dto) {
        CommercialDetails commercial = commercialDetailsRepository.findByUser_Id(userId)
                .orElseThrow(() -> new EntityNotFoundException("Commercial not found for userId=" + userId));

        String imageObjectKey = normalizeImageKey(dto.imageObjectKey());
        if (imageObjectKey != null) {
            assertImageBelongsToCommercial(imageObjectKey, commercial.getId());
            // Comprueba contra R2 que el objeto existe de verdad y que su contenido es una
            // imagen — sin esto, imageObjectKey sería texto libre que el cliente inventa.
            r2Service.validateUploadedObjectInBucket(
                    petsBucketName, imageObjectKey, maxImageSizeBytes, ALLOWED_IMAGE_TYPES);
        }

        CatalogIntegrationRequest request = new CatalogIntegrationRequest();
        request.setCommercial(commercial);
        request.setProductName(dto.productName());
        request.setDescription(dto.description());
        request.setImageObjectKey(imageObjectKey);
        request.setDesiredEffects(dto.desiredEffects());
        request.setStatus(CatalogRequestStatus.PENDING);

        return toResponse(requestRepository.save(request));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogIntegrationResponseDTO> getMyRequests(Long userId) {
        CommercialDetails commercial = commercialDetailsRepository.findByUser_Id(userId)
                .orElseThrow(() -> new EntityNotFoundException("Commercial not found for userId=" + userId));

        return requestRepository.findByCommercial_IdOrderByCreatedAtDesc(commercial.getId())
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogIntegrationResponseDTO> getAllRequests() {
        return requestRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogIntegrationResponseDTO> getRequestsByStatus(CatalogRequestStatus status) {
        return requestRepository.findByStatusOrderByCreatedAtAsc(status)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public CatalogIntegrationResponseDTO markInReview(Long requestId) {
        CatalogIntegrationRequest request = findOrThrow(requestId);
        assertStatus(request, CatalogRequestStatus.PENDING, "Solo se pueden revisar solicitudes en estado PENDING");
        request.setStatus(CatalogRequestStatus.IN_REVIEW);
        return toResponse(requestRepository.save(request));
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogIntegrationResponseDTO getRequestDetail(Long requestId) {
        return toResponse(findOrThrow(requestId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GameDesignerSummaryDTO> getActiveDesigners() {
        return designerDetailsRepository.findByActiveTrue().stream()
                .map(d -> new GameDesignerSummaryDTO(
                        d.getId(), d.getUser().getId(), d.getName(), d.getLastName(),
                        d.getDesignerCode(), d.getCampaignsDesigned()))
                .toList();
    }

    @Override
    public CatalogIntegrationResponseDTO approve(Long requestId, ApprovePetRequestDTO dto) {
        CatalogIntegrationRequest request = findOrThrow(requestId);
        assertNotFinal(request);

        request.setAssignedDesigner(findActiveDesigner(dto.designerUserId()));
        request.setAdminNotes(dto.adminNotes());
        request.setStatus(CatalogRequestStatus.APPROVED);
        // Arranca el borrador con lo que ya mandó el comercial para que el diseñador no
        // tenga que reescribirlo. La imagen de la solicitud entra como sprite inicial.
        if (request.getItemDraft() == null || request.getItemDraft().isEmpty()) {
            request.setItemDraft(seedDraftFrom(request));
        }
        log.info("Solicitud de catálogo {} aprobada y asignada al diseñador (userId={})",
                requestId, dto.designerUserId());
        return toResponse(requestRepository.save(request));
    }

    @Override
    public CatalogIntegrationResponseDTO assignDesigner(Long requestId, Long designerUserId) {
        CatalogIntegrationRequest request = findOrThrow(requestId);
        assertNotFinal(request);

        request.setAssignedDesigner(findActiveDesigner(designerUserId));
        log.info("Solicitud de catálogo {} reasignada al diseñador (userId={})", requestId, designerUserId);
        return toResponse(requestRepository.save(request));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogIntegrationResponseDTO> getAssignedRequests(Long designerUserId) {
        return requestRepository.findByAssignedDesigner_User_IdOrderByCreatedAtDesc(designerUserId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogIntegrationResponseDTO getAssignedRequestDetail(Long requestId, Long designerUserId) {
        return toResponse(findAssignedOrThrow(requestId, designerUserId));
    }

    @Override
    public CatalogIntegrationResponseDTO saveItemDraft(Long requestId, Long designerUserId, Map<String, Object> draft) {
        CatalogIntegrationRequest request = findAssignedOrThrow(requestId, designerUserId);
        assertNotFinal(request);

        if (request.getStatus() == CatalogRequestStatus.PENDING
                || request.getStatus() == CatalogRequestStatus.IN_REVIEW) {
            throw new IllegalStateException(
                    "Hay que aprobar la solicitud antes de armar el ítem. Estado actual: " + request.getStatus());
        }

        request.setItemDraft(draft);
        request.setStatus(CatalogRequestStatus.ITEM_IN_PROGRESS);
        return toResponse(requestRepository.save(request));
    }

    @Override
    public CatalogIntegrationResponseDTO publishCatalogItem(Long requestId, Long designerUserId) {
        CatalogIntegrationRequest request = findAssignedOrThrow(requestId, designerUserId);
        assertNotFinal(request);

        Map<String, Object> draft = request.getItemDraft();
        if (draft == null || draft.isEmpty()) {
            throw new IllegalStateException("No hay borrador que publicar para la solicitud " + requestId);
        }

        // El schema corre sobre el borrador COMPLETO, antes de quitarle el sprite:
        // así puede opinar también sobre spriteObjectKey.
        List<ValidationError> errors =
                schemaValidator.validate(PetSchemaValidator.PetSchema.CATALOG_ITEM, draft);
        if (!errors.isEmpty()) {
            throw new ValidationException(
                    "El ítem no está listo para publicarse: " + PetSchemaValidator.describe(errors));
        }

        // spriteObjectKey es la clave interna en R2 y no forma parte del DTO público
        // (que expone spriteUrl), así que se saca antes de convertir y se pasa aparte.
        Map<String, Object> payload = new HashMap<>(draft);
        Object spriteKey = payload.remove(DRAFT_SPRITE_KEY);

        PetCatalogItemRequestDTO itemDto;
        try {
            itemDto = objectMapper.convertValue(payload, PetCatalogItemRequestDTO.class);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("El borrador del ítem tiene campos inválidos: " + e.getMessage());
        }

        if (itemDto.name() == null || itemDto.name().isBlank()) {
            throw new ValidationException("El ítem necesita un nombre antes de publicarse");
        }

        var catalogItem = catalogService.createCatalogItem(
                itemDto, spriteKey instanceof String s ? s : null);

        request.setStatus(CatalogRequestStatus.COMPLETED);
        request.setResultCatalogItemId(catalogItem.id());
        log.info("Solicitud {} publicada como ítem de catálogo {}", requestId, catalogItem.id());
        return toResponse(requestRepository.save(request));
    }

    @Override
    public CatalogIntegrationResponseDTO reject(Long requestId, String reason) {
        CatalogIntegrationRequest request = findOrThrow(requestId);
        assertNotFinal(request);

        request.setStatus(CatalogRequestStatus.REJECTED);
        request.setRejectionReason(reason);
        return toResponse(requestRepository.save(request));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** La imagen es opcional: normaliza "" / espacios a null para no guardar claves vacías. */
    private String normalizeImageKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return null;
        return rawKey.trim();
    }

    /**
     * Una clave solo es aceptable si la emitió {@link #prepareImageUpload} para este mismo
     * comercial. Evita que un comercial reutilice la imagen de otro o apunte a un objeto
     * arbitrario del bucket de mascotas (p. ej. un sprite ya publicado).
     */
    private void assertImageBelongsToCommercial(String imageObjectKey, Long commercialId) {
        String expectedPrefix = IMAGE_KEY_PREFIX + commercialId + "/";
        if (!imageObjectKey.startsWith(expectedPrefix)) {
            log.warn("Comercial {} intentó usar una clave de imagen ajena o inválida: {}",
                    commercialId, imageObjectKey);
            throw new ValidationException(
                    "La imagen no corresponde a este comercial. Solicite una nueva URL de subida.");
        }
        // Sin subcarpetas ni traversal: exactamente catalog-requests/{id}/{uuid}
        String remainder = imageObjectKey.substring(expectedPrefix.length());
        if (remainder.isBlank() || remainder.contains("/") || remainder.contains("..")) {
            throw new ValidationException("Clave de imagen inválida.");
        }
    }

    // ── Hilo de conversación ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<CatalogRequestCommentDTO> getComments(Long requestId, Long userId, CommentAuthorRole role) {
        assertCanAccessThread(findOrThrow(requestId), userId, role);

        return commentRepository.findByCatalogRequestIdOrderByCreatedAtAsc(requestId)
                .stream()
                .map(c -> new CatalogRequestCommentDTO(
                        c.getId(), c.getContent(), c.getAuthorName(),
                        c.getAuthorRole(), c.getRelatedStatus(), c.getCreatedAt()))
                .toList();
    }

    @Override
    public CatalogRequestCommentDTO addComment(Long requestId, Long authorUserId,
                                               CommentAuthorRole role, String content) {
        CatalogIntegrationRequest request = findOrThrow(requestId);
        assertCanAccessThread(request, authorUserId, role);

        CatalogRequestComment comment = commentRepository.save(CatalogRequestComment.builder()
                .catalogRequest(request)
                .content(content)
                .authorUserId(authorUserId)
                .authorName(resolveAuthorName(authorUserId, role))
                .authorRole(role)
                // Se guarda el estado del momento: leído meses después, el hilo sigue
                // teniendo sentido aunque la solicitud ya haya avanzado.
                .relatedStatus(request.getStatus())
                .build());

        log.info("Comentario agregado a la solicitud {} por {} (userId={})", requestId, role, authorUserId);

        return new CatalogRequestCommentDTO(
                comment.getId(), comment.getContent(), comment.getAuthorName(),
                comment.getAuthorRole(), comment.getRelatedStatus(), comment.getCreatedAt());
    }

    /**
     * El hilo es privado a los tres involucrados. Sin esto, el id de la solicitud
     * sería suficiente para que cualquier comercial leyera —o escribiera en— la
     * conversación de otro.
     */
    private void assertCanAccessThread(CatalogIntegrationRequest request, Long userId, CommentAuthorRole role) {
        boolean permitido = switch (role) {
            case ADMIN -> true;
            case COMMERCIAL -> request.getCommercial() != null
                    && userId.equals(request.getCommercial().getUser().getId());
            case DESIGNER -> request.getAssignedDesigner() != null
                    && userId.equals(request.getAssignedDesigner().getUser().getId());
        };

        if (!permitido) {
            log.warn("Acceso denegado al hilo de la solicitud {}: userId={} rol={}",
                    request.getId(), userId, role);
            throw new EntityNotFoundException("Solicitud no encontrada id=" + request.getId());
        }
    }

    /**
     * El nombre se copia al comentario en vez de resolverse por join al leer: así el
     * hilo se mantiene legible aunque el perfil cambie o la cuenta se elimine.
     */
    private String resolveAuthorName(Long userId, CommentAuthorRole role) {
        return switch (role) {
            case COMMERCIAL -> commercialDetailsRepository.findByUser_Id(userId)
                    .map(CommercialDetails::getCompanyName).orElse("Comercial");
            case DESIGNER -> designerDetailsRepository.findByUser_Id(userId)
                    .map(d -> (d.getName() + " " + d.getLastName()).trim())
                    .orElse("Diseñador");
            case ADMIN -> "Administrador";
        };
    }

    private CatalogIntegrationRequest findOrThrow(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Solicitud no encontrada id=" + id));
    }

    /**
     * Igual que en branding: si la solicitud no está asignada a este diseñador, para él
     * no existe. Evita que un diseñador toque el trabajo de otro.
     */
    private CatalogIntegrationRequest findAssignedOrThrow(Long id, Long designerUserId) {
        return requestRepository.findByIdAndAssignedDesigner_User_Id(id, designerUserId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Solicitud no encontrada o no asignada a este diseñador: id=" + id));
    }

    private GameDesignerDetails findActiveDesigner(Long designerUserId) {
        GameDesignerDetails designer = designerDetailsRepository.findByUser_Id(designerUserId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Diseñador no encontrado para userId=" + designerUserId));
        if (!Boolean.TRUE.equals(designer.getActive())) {
            throw new ValidationException("El diseñador no está activo");
        }
        return designer;
    }

    private void assertStatus(CatalogIntegrationRequest r, CatalogRequestStatus expected, String msg) {
        if (r.getStatus() != expected) {
            throw new IllegalStateException(msg + ". Estado actual: " + r.getStatus());
        }
    }

    private void assertNotFinal(CatalogIntegrationRequest r) {
        if (r.getStatus() == CatalogRequestStatus.COMPLETED || r.getStatus() == CatalogRequestStatus.REJECTED) {
            throw new IllegalStateException("La solicitud ya fue finalizada con estado: " + r.getStatus());
        }
    }

    /**
     * Borrador inicial: las claves coinciden con los campos de PetCatalogItemRequestDTO
     * para que el front pueda mandar el formulario tal cual, más {@code spriteObjectKey}
     * con la imagen que subió el comercial.
     */
    private Map<String, Object> seedDraftFrom(CatalogIntegrationRequest r) {
        Map<String, Object> draft = new HashMap<>();
        draft.put("name", r.getProductName());
        draft.put("description", r.getDescription());
        draft.put("active", true);
        if (r.getImageObjectKey() != null) {
            draft.put(DRAFT_SPRITE_KEY, r.getImageObjectKey());
        }
        return draft;
    }

    private String designerDisplayName(GameDesignerDetails d) {
        String name = (d.getName() == null ? "" : d.getName())
                + " " + (d.getLastName() == null ? "" : d.getLastName());
        return name.isBlank() ? d.getDesignerCode() : name.trim();
    }

    private String buildPublicUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return null;
        if (petsCdnDomain != null && !petsCdnDomain.isBlank())
            return String.format("https://%s/%s", petsCdnDomain, objectKey);
        return String.format("https://%s.r2.dev/%s", petsBucketName, objectKey);
    }

    private CatalogIntegrationResponseDTO toResponse(CatalogIntegrationRequest r) {
        return new CatalogIntegrationResponseDTO(
                r.getId(),
                r.getCommercial().getCompanyName(),
                r.getProductName(),
                r.getDescription(),
                buildPublicUrl(r.getImageObjectKey()),
                r.getDesiredEffects(),
                r.getStatus(),
                r.getRejectionReason(),
                r.getAssignedDesigner() == null ? null : r.getAssignedDesigner().getUser().getId(),
                r.getAssignedDesigner() == null ? null : designerDisplayName(r.getAssignedDesigner()),
                r.getAdminNotes(),
                r.getItemDraft(),
                r.getResultCatalogItemId(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}