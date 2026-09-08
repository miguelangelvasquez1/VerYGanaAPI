package com.verygana2.services.pqrs;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.verygana2.config.pqrs.PqrsSlaProperties;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.pqrs.requests.CreatePqrsRequestDTO;
import com.verygana2.dtos.pqrs.requests.RespondPqrsRequestDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAdminDetailDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAssetResponseDTO;
import com.verygana2.dtos.pqrs.responses.PqrsResponseDTO;
import com.verygana2.exceptions.pqrsExceptions.PqrsAccessDeniedException;
import com.verygana2.mappers.pqrs.PqrsAssetMapper;
import com.verygana2.mappers.pqrs.PqrsMapper;
import com.verygana2.models.User;
import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.enums.pqrs.MarketplaceIssueReason;
import com.verygana2.models.enums.pqrs.PqrsResolutionAction;
import com.verygana2.models.enums.pqrs.PqrsStatus;
import com.verygana2.models.enums.pqrs.PqrsType;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.models.pqrs.PqrsAsset;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.repositories.UserRepository;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;
import com.verygana2.repositories.pqrs.PqrsRepository;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.marketplace.PurchaseItemRefundService;
import com.verygana2.services.interfaces.pqrs.PqrsAssetService;
import com.verygana2.utils.pqrs.BusinessDayCalculator;
import com.verygana2.utils.pqrs.RequesterNameResolver;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PqrsServiceImpl}: cubren los 3 flujos del sistema de PQRS
 * (radicación por el usuario, gestión por el admin asignado y los dos jobs
 * programados de reintento de asignación / alertas de SLA). Los mocks reemplazan
 * la base de datos y los servicios de correo/notificación para aislar solo la
 * lógica de negocio de esta clase.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PqrsServiceImpl")
class PqrsServiceImplTest {

    @Mock private PqrsRepository pqrsRepository;
    @Mock private UserRepository userRepository;
    @Mock private PurchaseItemRepository purchaseItemRepository;
    @Mock private PqrsAssignmentService pqrsAssignmentService;
    @Mock private PqrsMapper pqrsMapper;
    @Mock private EmailService emailService;
    @Mock private NotificationService notificationService;
    @Mock private BusinessDayCalculator businessDayCalculator;
    @Mock private PqrsSlaProperties pqrsSlaProperties;
    @Mock private RequesterNameResolver requesterNameResolver;
    @Mock private PurchaseItemRefundService purchaseItemRefundService;
    @Mock private PqrsAssetService pqrsAssetService;
    @Mock private PqrsAssetMapper pqrsAssetMapper;

    private PqrsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PqrsServiceImpl(pqrsRepository, userRepository, purchaseItemRepository, pqrsAssignmentService,
                pqrsMapper, emailService, notificationService, businessDayCalculator, pqrsSlaProperties,
                requesterNameResolver, purchaseItemRefundService, pqrsAssetService, pqrsAssetMapper);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private User requester(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("consumidor@test.com");
        return user;
    }

    private AdminDetails admin(Long userId) {
        User adminUser = new User();
        adminUser.setId(userId);
        adminUser.setEmail("admin@test.com");
        AdminDetails details = new AdminDetails();
        details.setUser(adminUser);
        return details;
    }

    private Pqrs pqrs(Long id, PqrsStatus status, User requester, AdminDetails assignedAdmin) {
        return Pqrs.builder()
                .id(id)
                .type(PqrsType.PETICION)
                .status(status)
                .requester(requester)
                .assignedAdmin(assignedAdmin)
                .subject("Asunto")
                .description("Descripción")
                .dueDate(ZonedDateTime.now().plusDays(15))
                .build();
    }

    private PurchaseItem purchaseItem(Long id) {
        Purchase purchase = Purchase.builder().id(100L).build();
        PurchaseItem item = new PurchaseItem();
        item.setId(id);
        item.setPurchase(purchase);
        item.setStatus(PurchaseItemStatus.CLAIMED);
        return item;
    }

    private Pqrs marketplacePqrs(Long id, PqrsStatus status, User requester, AdminDetails assignedAdmin,
            PurchaseItem item, MarketplaceIssueReason reason) {
        return Pqrs.builder()
                .id(id)
                .type(PqrsType.RECLAMO)
                .status(status)
                .requester(requester)
                .assignedAdmin(assignedAdmin)
                .subject("Reclamo")
                .description("Descripción")
                .dueDate(ZonedDateTime.now().plusDays(15))
                .purchaseItem(item)
                .reasonCode(reason)
                .build();
    }

    // ─── createPqrs ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createPqrs")
    class CreatePqrs {

        @Test
        @DisplayName("con admin disponible: asigna el admin, notifica y responde el DTO mapeado")
        void withAvailableAdmin_assignsAdminAndNotifies() {
            CreatePqrsRequestDTO dto = new CreatePqrsRequestDTO();
            dto.setType(PqrsType.PETICION);
            dto.setSubject("Asunto");
            dto.setDescription("Descripción");

            User requester = requester(1L);
            AdminDetails assignedAdmin = admin(99L);
            Pqrs saved = pqrs(10L, PqrsStatus.RECIBIDA, requester, assignedAdmin);
            PqrsResponseDTO expectedResponse = new PqrsResponseDTO();

            when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
            when(pqrsSlaProperties.getSlaDaysFor(PqrsType.PETICION)).thenReturn(15);
            when(businessDayCalculator.addBusinessDays(any(), eq(15))).thenReturn(ZonedDateTime.now().plusDays(21));
            when(pqrsAssignmentService.pickNextAdmin()).thenReturn(Optional.of(assignedAdmin));
            when(pqrsRepository.save(any(Pqrs.class))).thenReturn(saved);
            when(pqrsAssetService.validateAndClaimAssets(null, 1L, saved)).thenReturn(List.of());
            // Se resuelve el nombre tanto del solicitante (email de confirmación) como
            // del admin recién asignado (notificación in-app y email de asignación).
            when(requesterNameResolver.resolve(any(User.class))).thenReturn("Nombre Resuelto");
            when(pqrsMapper.toResponseDTO(saved)).thenReturn(expectedResponse);

            PqrsResponseDTO result = service.createPqrs(dto, 1L);

            assertThat(result).isSameAs(expectedResponse);
            // Se notifica al admin asignado (in-app + email) porque sí quedó asignado.
            verify(notificationService).createInternalNotification(eq(99L), anyString(), anyString(), any(Instant.class));
            verify(emailService).sendPqrsAssignedToAdmin(eq("admin@test.com"), anyString(), anyString(), anyString(), any());
            // Siempre se confirma la radicación al solicitante, tenga o no admin asignado.
            verify(emailService).sendPqrsReceivedConfirmation(eq("consumidor@test.com"), anyString(), anyString(),
                    eq(PqrsType.PETICION), any());
        }

        @Test
        @DisplayName("sin admin disponible: guarda el PQRS sin asignar y NO envía notificación de asignación")
        void withNoAvailableAdmin_savesUnassignedWithoutNotifyingAdmin() {
            CreatePqrsRequestDTO dto = new CreatePqrsRequestDTO();
            dto.setType(PqrsType.QUEJA);
            dto.setSubject("Asunto");
            dto.setDescription("Descripción");

            User requester = requester(1L);
            Pqrs saved = pqrs(11L, PqrsStatus.PENDIENTE_ASIGNACION, requester, null);

            when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
            when(pqrsSlaProperties.getSlaDaysFor(PqrsType.QUEJA)).thenReturn(15);
            when(businessDayCalculator.addBusinessDays(any(), eq(15))).thenReturn(ZonedDateTime.now().plusDays(21));
            when(pqrsAssignmentService.pickNextAdmin()).thenReturn(Optional.empty());
            when(pqrsRepository.save(any(Pqrs.class))).thenReturn(saved);
            when(pqrsAssetService.validateAndClaimAssets(null, 1L, saved)).thenReturn(List.of());
            when(requesterNameResolver.resolve(requester)).thenReturn("Juan Pérez");
            when(pqrsMapper.toResponseDTO(saved)).thenReturn(new PqrsResponseDTO());

            service.createPqrs(dto, 1L);

            verify(notificationService, never()).createInternalNotification(any(), any(), any(), any());
            verify(emailService, never()).sendPqrsAssignedToAdmin(any(), any(), any(), any(), any());
            verify(emailService).sendPqrsReceivedConfirmation(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("usuario solicitante inexistente: lanza EntityNotFoundException y no persiste nada")
        void requesterNotFound_throwsEntityNotFoundException() {
            CreatePqrsRequestDTO dto = new CreatePqrsRequestDTO();
            dto.setType(PqrsType.RECLAMO);

            when(userRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createPqrs(dto, 404L))
                    .isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(pqrsRepository, pqrsAssignmentService, emailService, notificationService, pqrsAssetService);
        }
    }

    // ─── createPqrsForPurchaseItem ─────────────────────────────────────────────

    @Nested
    @DisplayName("createPqrsForPurchaseItem")
    class CreatePqrsForPurchaseItem {

        @Test
        @DisplayName("crea el PQRS con type=RECLAMO, vinculado al ítem, con el motivo indicado")
        void createsReclamoLinkedToItemWithReason() {
            User requester = requester(1L);
            PurchaseItem item = purchaseItem(5L);
            AdminDetails assignedAdmin = admin(99L);
            Pqrs saved = marketplacePqrs(20L, PqrsStatus.RECIBIDA, requester, assignedAdmin, item,
                    MarketplaceIssueReason.CODE_INVALID);

            when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
            when(pqrsSlaProperties.getSlaDaysFor(PqrsType.RECLAMO)).thenReturn(15);
            when(businessDayCalculator.addBusinessDays(any(), eq(15))).thenReturn(ZonedDateTime.now().plusDays(21));
            when(pqrsAssignmentService.pickNextAdmin()).thenReturn(Optional.of(assignedAdmin));
            when(requesterNameResolver.resolve(any(User.class))).thenReturn("Nombre Resuelto");
            when(pqrsMapper.toResponseDTO(any(Pqrs.class))).thenReturn(new PqrsResponseDTO());

            var captor = ArgumentCaptor.forClass(Pqrs.class);
            when(pqrsRepository.save(captor.capture())).thenReturn(saved);
            when(pqrsAssetService.validateAndClaimAssets(null, 1L, saved)).thenReturn(List.of());

            service.createPqrsForPurchaseItem(item, MarketplaceIssueReason.CODE_INVALID, "El código no funciona", 1L, null);

            Pqrs persisted = captor.getValue();
            assertThat(persisted.getType()).isEqualTo(PqrsType.RECLAMO);
            assertThat(persisted.getPurchaseItem()).isSameAs(item);
            assertThat(persisted.getReasonCode()).isEqualTo(MarketplaceIssueReason.CODE_INVALID);
            assertThat(persisted.getDescription()).isEqualTo("El código no funciona");

            // El ítem entra en revisión y guarda su status previo para poder
            // restaurarlo si el admin descarta el reclamo.
            assertThat(item.getStatus()).isEqualTo(PurchaseItemStatus.IN_REVIEW);
            assertThat(item.getStatusBeforeReview()).isEqualTo(PurchaseItemStatus.CLAIMED);
            verify(purchaseItemRepository).save(item);
        }

        @Test
        @DisplayName("con evidencia adjunta: reclama los assets para el Pqrs creado y los devuelve en el DTO")
        void withAssetIds_claimsAssetsAndReturnsThemInResponse() {
            User requester = requester(1L);
            PurchaseItem item = purchaseItem(5L);
            AdminDetails assignedAdmin = admin(99L);
            Pqrs saved = marketplacePqrs(21L, PqrsStatus.RECIBIDA, requester, assignedAdmin, item,
                    MarketplaceIssueReason.NOT_AS_DESCRIBED);
            List<Long> assetIds = List.of(100L, 101L);
            PqrsAsset claimedAsset = PqrsAsset.builder().id(100L).ownerUserId(1L).objectKey("pqrs-evidence/1/a").build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
            when(pqrsSlaProperties.getSlaDaysFor(PqrsType.RECLAMO)).thenReturn(15);
            when(businessDayCalculator.addBusinessDays(any(), eq(15))).thenReturn(ZonedDateTime.now().plusDays(21));
            when(pqrsAssignmentService.pickNextAdmin()).thenReturn(Optional.of(assignedAdmin));
            when(requesterNameResolver.resolve(any(User.class))).thenReturn("Nombre Resuelto");
            when(pqrsRepository.save(any(Pqrs.class))).thenReturn(saved);
            when(pqrsAssetService.validateAndClaimAssets(assetIds, 1L, saved)).thenReturn(List.of(claimedAsset));
            when(pqrsMapper.toResponseDTO(saved)).thenReturn(new PqrsResponseDTO());
            PqrsAssetResponseDTO mappedAsset = new PqrsAssetResponseDTO();
            mappedAsset.setViewUrl("/pqrs/assets/100/view");
            when(pqrsAssetMapper.toResponseDTO(claimedAsset)).thenReturn(mappedAsset);

            PqrsResponseDTO result = service.createPqrsForPurchaseItem(
                    item, MarketplaceIssueReason.NOT_AS_DESCRIBED, "No corresponde", 1L, assetIds);

            assertThat(result.getAssets()).hasSize(1);
            assertThat(result.getAssets().get(0).getViewUrl()).isEqualTo("/pqrs/assets/100/view");
            verify(pqrsAssetService).validateAndClaimAssets(assetIds, 1L, saved);
        }

        @Test
        @DisplayName("evidencia inválida (ej. dueño incorrecto): la excepción se propaga y no se envían notificaciones")
        void invalidAssets_propagatesExceptionWithoutNotifying() {
            User requester = requester(1L);
            PurchaseItem item = purchaseItem(5L);
            AdminDetails assignedAdmin = admin(99L);
            Pqrs saved = marketplacePqrs(22L, PqrsStatus.RECIBIDA, requester, assignedAdmin, item,
                    MarketplaceIssueReason.CODE_INVALID);
            List<Long> assetIds = List.of(999L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
            when(pqrsSlaProperties.getSlaDaysFor(PqrsType.RECLAMO)).thenReturn(15);
            when(businessDayCalculator.addBusinessDays(any(), eq(15))).thenReturn(ZonedDateTime.now().plusDays(21));
            when(pqrsAssignmentService.pickNextAdmin()).thenReturn(Optional.of(assignedAdmin));
            when(pqrsRepository.save(any(Pqrs.class))).thenReturn(saved);
            when(pqrsAssetService.validateAndClaimAssets(assetIds, 1L, saved))
                    .thenThrow(new PqrsAccessDeniedException("El archivo 999 no pertenece a este usuario"));

            assertThatThrownBy(() -> service.createPqrsForPurchaseItem(
                    item, MarketplaceIssueReason.CODE_INVALID, "x", 1L, assetIds))
                    .isInstanceOf(PqrsAccessDeniedException.class);

            verifyNoInteractions(emailService, notificationService);
        }
    }

    // ─── getMyPqrs / getMyPqrsDetail (vista del solicitante) ──────────────────

    @Nested
    @DisplayName("getMyPqrs")
    class GetMyPqrs {

        @Test
        @DisplayName("delega en el repositorio filtrando por requesterId y mapea la página a DTO")
        void returnsPagedResponseMappedFromRepository() {
            Pqrs pqrs = pqrs(1L, PqrsStatus.RECIBIDA, requester(1L), null);
            Pageable pageable = Pageable.ofSize(20);
            Page<Pqrs> page = new PageImpl<>(List.of(pqrs));

            when(pqrsRepository.findByRequesterId(1L, pageable)).thenReturn(page);
            when(pqrsMapper.toResponseDTO(pqrs)).thenReturn(new PqrsResponseDTO());

            PagedResponse<PqrsResponseDTO> result = service.getMyPqrs(1L, pageable);

            assertThat(result.getData()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getMyPqrsDetail")
    class GetMyPqrsDetail {

        @Test
        @DisplayName("PQRS propio: retorna el detalle mapeado")
        void ownPqrs_returnsMappedDetail() {
            User requester = requester(1L);
            Pqrs pqrs = pqrs(5L, PqrsStatus.RECIBIDA, requester, null);
            PqrsResponseDTO expected = new PqrsResponseDTO();

            when(pqrsRepository.findById(5L)).thenReturn(Optional.of(pqrs));
            when(pqrsMapper.toResponseDTO(pqrs)).thenReturn(expected);

            assertThat(service.getMyPqrsDetail(5L, 1L)).isSameAs(expected);
        }

        @Test
        @DisplayName("PQRS inexistente: lanza EntityNotFoundException")
        void notFound_throwsEntityNotFoundException() {
            when(pqrsRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMyPqrsDetail(999L, 1L))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("PQRS de otro usuario: lanza EntityNotFoundException (oculta la existencia, no es un 403)")
        void belongsToAnotherUser_throwsEntityNotFoundException() {
            Pqrs pqrs = pqrs(5L, PqrsStatus.RECIBIDA, requester(1L), null);
            when(pqrsRepository.findById(5L)).thenReturn(Optional.of(pqrs));

            // El requester autenticado (2L) no es el dueño (1L) del PQRS.
            assertThatThrownBy(() -> service.getMyPqrsDetail(5L, 2L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ─── Vista y acciones del admin asignado ───────────────────────────────────

    @Nested
    @DisplayName("getAssignedPqrs")
    class GetAssignedPqrs {

        @Test
        @DisplayName("delega en el repositorio con los filtros de status/type y mapea a DTO de admin")
        void delegatesToRepositoryWithFilters() {
            Pqrs pqrs = pqrs(1L, PqrsStatus.RECIBIDA, requester(1L), admin(99L));
            Pageable pageable = Pageable.ofSize(20);
            Page<Pqrs> page = new PageImpl<>(List.of(pqrs));

            when(pqrsRepository.findByAssignedAdminWithFilters(99L, PqrsStatus.RECIBIDA, null, pageable))
                    .thenReturn(page);
            when(pqrsMapper.toAdminDetailDTO(pqrs)).thenReturn(new PqrsAdminDetailDTO());

            PagedResponse<PqrsAdminDetailDTO> result = service.getAssignedPqrs(99L, PqrsStatus.RECIBIDA, null, pageable);

            assertThat(result.getData()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getPqrsDetailForAdmin")
    class GetPqrsDetailForAdmin {

        @Test
        @DisplayName("PQRS asignado a este admin: retorna el detalle de admin mapeado")
        void assignedToThisAdmin_returnsMappedDetail() {
            Pqrs pqrs = pqrs(1L, PqrsStatus.RECIBIDA, requester(1L), admin(99L));
            PqrsAdminDetailDTO expected = new PqrsAdminDetailDTO();

            when(pqrsRepository.findById(1L)).thenReturn(Optional.of(pqrs));
            when(pqrsMapper.toAdminDetailDTO(pqrs)).thenReturn(expected);

            assertThat(service.getPqrsDetailForAdmin(1L, 99L)).isSameAs(expected);
        }

        @Test
        @DisplayName("PQRS asignado a otro admin: lanza PqrsAccessDeniedException")
        void assignedToAnotherAdmin_throwsAccessDenied() {
            Pqrs pqrs = pqrs(1L, PqrsStatus.RECIBIDA, requester(1L), admin(99L));
            when(pqrsRepository.findById(1L)).thenReturn(Optional.of(pqrs));

            assertThatThrownBy(() -> service.getPqrsDetailForAdmin(1L, 1L))
                    .isInstanceOf(PqrsAccessDeniedException.class);
        }

        @Test
        @DisplayName("PQRS sin admin asignado todavía: lanza PqrsAccessDeniedException")
        void unassigned_throwsAccessDenied() {
            Pqrs pqrs = pqrs(1L, PqrsStatus.PENDIENTE_ASIGNACION, requester(1L), null);
            when(pqrsRepository.findById(1L)).thenReturn(Optional.of(pqrs));

            assertThatThrownBy(() -> service.getPqrsDetailForAdmin(1L, 99L))
                    .isInstanceOf(PqrsAccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("markUnderReview")
    class MarkUnderReview {

        @Test
        @DisplayName("PQRS en RECIBIDA: pasa a EN_REVISION y se persiste")
        void receivedStatus_movesToEnRevision() {
            Pqrs pqrs = pqrs(1L, PqrsStatus.RECIBIDA, requester(1L), admin(99L));
            when(pqrsRepository.findById(1L)).thenReturn(Optional.of(pqrs));

            service.markUnderReview(1L, 99L);

            ArgumentCaptor<Pqrs> captor = ArgumentCaptor.forClass(Pqrs.class);
            verify(pqrsRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PqrsStatus.EN_REVISION);
        }

        @Test
        @DisplayName("PQRS que ya no está en RECIBIDA: lanza ValidationException y no persiste")
        void wrongStatus_throwsValidationException() {
            Pqrs pqrs = pqrs(1L, PqrsStatus.RESUELTA, requester(1L), admin(99L));
            when(pqrsRepository.findById(1L)).thenReturn(Optional.of(pqrs));

            assertThatThrownBy(() -> service.markUnderReview(1L, 99L))
                    .isInstanceOf(ValidationException.class);

            verify(pqrsRepository, never()).save(any());
        }

        @Test
        @DisplayName("admin no dueño del PQRS: lanza PqrsAccessDeniedException")
        void notOwnedByAdmin_throwsAccessDenied() {
            Pqrs pqrs = pqrs(1L, PqrsStatus.RECIBIDA, requester(1L), admin(99L));
            when(pqrsRepository.findById(1L)).thenReturn(Optional.of(pqrs));

            assertThatThrownBy(() -> service.markUnderReview(1L, 1L))
                    .isInstanceOf(PqrsAccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("respondToPqrs")
    class RespondToPqrs {

        @Test
        @DisplayName("PQRS resoluble: guarda la respuesta, marca RESUELTA y notifica al solicitante")
        void resolvablePqrs_marksResolvedAndNotifiesRequester() {
            User requester = requester(1L);
            Pqrs pqrs = pqrs(1L, PqrsStatus.EN_REVISION, requester, admin(99L));
            RespondPqrsRequestDTO dto = new RespondPqrsRequestDTO();
            dto.setResponse("Solucionado");

            when(pqrsRepository.findById(1L)).thenReturn(Optional.of(pqrs));
            when(pqrsRepository.save(any(Pqrs.class))).thenAnswer(inv -> inv.getArgument(0));
            when(requesterNameResolver.resolve(requester)).thenReturn("Juan Pérez");

            service.respondToPqrs(1L, dto, 99L);

            assertThat(pqrs.getStatus()).isEqualTo(PqrsStatus.RESUELTA);
            assertThat(pqrs.getResponse()).isEqualTo("Solucionado");
            assertThat(pqrs.getResolvedAt()).isNotNull();
            verify(notificationService).createInternalNotification(eq(1L), anyString(), anyString(), any(Instant.class));
            verify(emailService).sendPqrsResolved(eq("consumidor@test.com"), anyString(), anyString(), eq("Solucionado"));
        }

        @Test
        @DisplayName("PQRS ya resuelta: lanza ValidationException y no notifica")
        void alreadyResolved_throwsValidationException() {
            Pqrs pqrs = pqrs(1L, PqrsStatus.RESUELTA, requester(1L), admin(99L));
            RespondPqrsRequestDTO dto = new RespondPqrsRequestDTO();
            dto.setResponse("Solucionado otra vez");

            when(pqrsRepository.findById(1L)).thenReturn(Optional.of(pqrs));

            assertThatThrownBy(() -> service.respondToPqrs(1L, dto, 99L))
                    .isInstanceOf(ValidationException.class);

            verifyNoInteractions(emailService, notificationService);
        }

        @Test
        @DisplayName("admin no dueño del PQRS: lanza PqrsAccessDeniedException")
        void notOwnedByAdmin_throwsAccessDenied() {
            Pqrs pqrs = pqrs(1L, PqrsStatus.RECIBIDA, requester(1L), admin(99L));
            RespondPqrsRequestDTO dto = new RespondPqrsRequestDTO();
            dto.setResponse("x");
            when(pqrsRepository.findById(1L)).thenReturn(Optional.of(pqrs));

            assertThatThrownBy(() -> service.respondToPqrs(1L, dto, 1L))
                    .isInstanceOf(PqrsAccessDeniedException.class);
        }

        @Test
        @DisplayName("PQRS vinculado a un ítem sin action: lanza ValidationException y no ejecuta ningún reembolso")
        void marketplaceLinkedWithoutAction_throwsValidationException() {
            PurchaseItem item = purchaseItem(5L);
            Pqrs pqrs = marketplacePqrs(1L, PqrsStatus.RECIBIDA, requester(1L), admin(99L), item,
                    MarketplaceIssueReason.NOT_DELIVERED);
            RespondPqrsRequestDTO dto = new RespondPqrsRequestDTO();
            dto.setResponse("x");
            when(pqrsRepository.findById(1L)).thenReturn(Optional.of(pqrs));

            assertThatThrownBy(() -> service.respondToPqrs(1L, dto, 99L))
                    .isInstanceOf(ValidationException.class);

            verifyNoInteractions(purchaseItemRefundService);
        }

        @Test
        @DisplayName("PQRS vinculado a un ítem con action=DISMISS: restaura el status previo del ítem y resuelve sin ejecutar reembolso")
        void marketplaceLinkedWithDismiss_resolvesWithoutRefund() {
            PurchaseItem item = purchaseItem(5L);
            item.enterReview(); // simula que el ítem ya estaba IN_REVIEW (guardó CLAIMED como previo)
            Pqrs pqrs = marketplacePqrs(1L, PqrsStatus.RECIBIDA, requester(1L), admin(99L), item,
                    MarketplaceIssueReason.NOT_DELIVERED);
            RespondPqrsRequestDTO dto = new RespondPqrsRequestDTO();
            dto.setResponse("El código sí era válido");
            dto.setAction(PqrsResolutionAction.DISMISS);

            when(pqrsRepository.findById(1L)).thenReturn(Optional.of(pqrs));
            when(pqrsRepository.save(any(Pqrs.class))).thenAnswer(inv -> inv.getArgument(0));
            when(requesterNameResolver.resolve(any(User.class))).thenReturn("Juan Pérez");

            service.respondToPqrs(1L, dto, 99L);

            assertThat(pqrs.getStatus()).isEqualTo(PqrsStatus.RESUELTA);
            assertThat(pqrs.getAction()).isEqualTo(PqrsResolutionAction.DISMISS);
            assertThat(item.getStatus()).isEqualTo(PurchaseItemStatus.CLAIMED);
            assertThat(item.getStatusBeforeReview()).isNull();
            verify(purchaseItemRepository).save(item);
            verifyNoInteractions(purchaseItemRefundService);
        }

        @Test
        @DisplayName("PQRS vinculado a un ítem con action=REFUND: dispara el reembolso antes de resolver")
        void marketplaceLinkedWithRefund_triggersRefund() {
            PurchaseItem item = purchaseItem(5L);
            Pqrs pqrs = marketplacePqrs(1L, PqrsStatus.RECIBIDA, requester(1L), admin(99L), item,
                    MarketplaceIssueReason.CODE_INVALID);
            RespondPqrsRequestDTO dto = new RespondPqrsRequestDTO();
            dto.setResponse("Confirmado: el código estaba mal");
            dto.setAction(PqrsResolutionAction.REFUND);

            when(pqrsRepository.findById(1L)).thenReturn(Optional.of(pqrs));
            when(pqrsRepository.save(any(Pqrs.class))).thenAnswer(inv -> inv.getArgument(0));
            when(requesterNameResolver.resolve(any(User.class))).thenReturn("Juan Pérez");

            service.respondToPqrs(1L, dto, 99L);

            assertThat(pqrs.getStatus()).isEqualTo(PqrsStatus.RESUELTA);
            assertThat(pqrs.getAction()).isEqualTo(PqrsResolutionAction.REFUND);
            verify(purchaseItemRefundService).refund(item, MarketplaceIssueReason.CODE_INVALID, pqrs);
        }

        @Test
        @DisplayName("PQRS vinculado a un ítem con action=REFUND y porción en efectivo pendiente: queda en PENDIENTE_PAGO_REEMBOLSO, no notifica resolución todavía")
        void marketplaceLinkedWithRefundAndCashPortion_staysOpenUntilPaid() {
            PurchaseItem item = purchaseItem(5L);
            Pqrs pqrs = marketplacePqrs(1L, PqrsStatus.RECIBIDA, requester(1L), admin(99L), item,
                    MarketplaceIssueReason.CODE_INVALID);
            RespondPqrsRequestDTO dto = new RespondPqrsRequestDTO();
            dto.setResponse("Confirmado: el código estaba mal");
            dto.setAction(PqrsResolutionAction.REFUND);

            when(pqrsRepository.findById(1L)).thenReturn(Optional.of(pqrs));
            when(pqrsRepository.save(any(Pqrs.class))).thenAnswer(inv -> inv.getArgument(0));
            when(purchaseItemRefundService.refund(item, MarketplaceIssueReason.CODE_INVALID, pqrs))
                    .thenReturn(com.verygana2.models.finance.PurchaseItemCashRefund.builder().build());

            service.respondToPqrs(1L, dto, 99L);

            assertThat(pqrs.getStatus()).isEqualTo(PqrsStatus.PENDIENTE_PAGO_REEMBOLSO);
            assertThat(pqrs.getAction()).isEqualTo(PqrsResolutionAction.REFUND);
            assertThat(pqrs.getResolvedAt()).isNull();
            verifyNoInteractions(emailService, notificationService);
        }
    }

    // ─── Jobs programados ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("retryPendingAssignments")
    class RetryPendingAssignments {

        @Test
        @DisplayName("sin PQRS pendientes: no hace nada")
        void noPending_doesNothing() {
            when(pqrsRepository.findByStatus(PqrsStatus.PENDIENTE_ASIGNACION)).thenReturn(List.of());

            service.retryPendingAssignments();

            verify(pqrsRepository, never()).save(any());
            verifyNoInteractions(pqrsAssignmentService);
        }

        @Test
        @DisplayName("con admin disponible: asigna, pasa a RECIBIDA y notifica al admin")
        void adminAvailable_assignsAndNotifies() {
            Pqrs pending = pqrs(1L, PqrsStatus.PENDIENTE_ASIGNACION, requester(1L), null);
            AdminDetails newAdmin = admin(77L);

            when(pqrsRepository.findByStatus(PqrsStatus.PENDIENTE_ASIGNACION)).thenReturn(List.of(pending));
            when(pqrsAssignmentService.pickNextAdmin()).thenReturn(Optional.of(newAdmin));
            when(pqrsRepository.save(any(Pqrs.class))).thenAnswer(inv -> inv.getArgument(0));

            service.retryPendingAssignments();

            assertThat(pending.getStatus()).isEqualTo(PqrsStatus.RECIBIDA);
            assertThat(pending.getAssignedAdmin()).isEqualTo(newAdmin);
            verify(notificationService).createInternalNotification(eq(77L), anyString(), anyString(), any(Instant.class));
        }

        @Test
        @DisplayName("sin admin disponible todavía: deja el PQRS sin cambios")
        void noAdminAvailable_leavesPqrsUnchanged() {
            Pqrs pending = pqrs(1L, PqrsStatus.PENDIENTE_ASIGNACION, requester(1L), null);

            when(pqrsRepository.findByStatus(PqrsStatus.PENDIENTE_ASIGNACION)).thenReturn(List.of(pending));
            when(pqrsAssignmentService.pickNextAdmin()).thenReturn(Optional.empty());

            service.retryPendingAssignments();

            assertThat(pending.getStatus()).isEqualTo(PqrsStatus.PENDIENTE_ASIGNACION);
            verify(pqrsRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("sendSlaAlerts")
    class SendSlaAlerts {

        @Test
        @DisplayName("PQRS en riesgo con admin asignado: envía la alerta por email")
        void atRiskWithAssignedAdmin_sendsEmail() {
            AdminDetails assignedAdmin = admin(99L);
            Pqrs atRisk = pqrs(1L, PqrsStatus.RECIBIDA, requester(1L), assignedAdmin);

            when(pqrsRepository.findByStatusInAndDueDateBefore(any(), any())).thenReturn(List.of(atRisk));
            when(requesterNameResolver.resolve(assignedAdmin.getUser())).thenReturn("Admin Uno");

            service.sendSlaAlerts(2);

            verify(emailService).sendPqrsSlaAlert(eq("admin@test.com"), eq("Admin Uno"), anyString(), any());
        }

        @Test
        @DisplayName("PQRS en riesgo sin admin asignado: no falla y no envía correo")
        void atRiskWithoutAssignedAdmin_skipsSilently() {
            Pqrs atRisk = pqrs(1L, PqrsStatus.PENDIENTE_ASIGNACION, requester(1L), null);
            when(pqrsRepository.findByStatusInAndDueDateBefore(any(), any())).thenReturn(List.of(atRisk));

            service.sendSlaAlerts(2);

            verify(emailService, never()).sendPqrsSlaAlert(any(), any(), any(), any());
        }

        @Test
        @DisplayName("sin PQRS en riesgo: no envía ningún correo")
        void noneAtRisk_sendsNothing() {
            when(pqrsRepository.findByStatusInAndDueDateBefore(any(), any())).thenReturn(List.of());

            service.sendSlaAlerts(2);

            verify(emailService, times(0)).sendPqrsSlaAlert(any(), any(), any(), any());
        }
    }
}
