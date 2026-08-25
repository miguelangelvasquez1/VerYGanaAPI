package com.verygana2.services.pet;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.pet.ApprovePetRequestDTO;
import com.verygana2.dtos.pet.CatalogIntegrationRequestDTO;
import com.verygana2.dtos.pet.PetCatalogItemRequestDTO;
import com.verygana2.dtos.pet.PetCatalogItemResponseDTO;
import com.verygana2.dtos.pet.PetImageUploadPermissionDTO;
import com.verygana2.dtos.pet.PetImageUploadRequestDTO;
import com.verygana2.models.enums.CatalogRequestStatus;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.pets.CatalogIntegrationRequest;
import com.verygana2.models.User;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.GameDesignerDetails;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.details.GameDesignerDetailsRepository;
import com.verygana2.repositories.pet.CatalogIntegrationRequestRepository;
import com.verygana2.services.interfaces.pet.PetCatalogService;
import com.verygana2.storage.service.R2Service;
import com.verygana2.repositories.pet.CatalogRequestCommentRepository;
import com.verygana2.utils.validators.pet.PetSchemaValidator;
import com.verygana2.utils.validators.games.SchemaValidator;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de la subida de imagen en la solicitud de integración al catálogo de
 * mascotas: la emisión del permiso pre-firmado contra el bucket de mascotas y,
 * sobre todo, la validación de la clave al crear la solicitud — sin ella
 * {@code imageObjectKey} sería texto libre que el cliente inventa.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogIntegrationRequestServiceImpl — imagen del producto")
class CatalogIntegrationRequestServiceImplTest {

    private static final String PETS_BUCKET = "verygana-pets";
    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final Long USER_ID = 42L;

    private static final Long DESIGNER_USER_ID = 6L;

    @Mock private CatalogIntegrationRequestRepository requestRepository;
    @Mock private CommercialDetailsRepository commercialDetailsRepository;
    @Mock private GameDesignerDetailsRepository designerDetailsRepository;
    @Mock private PetCatalogService catalogService;
    @Mock private R2Service r2Service;
    @Mock private CatalogRequestCommentRepository commentRepository;
    @Mock private com.verygana2.repositories.finance.KeyTransactionRepository keyTransactionRepository;

    private CatalogIntegrationRequestServiceImpl service;
    private CommercialDetails commercial;
    private GameDesignerDetails designer;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        // El validador es real, no mock: así los tests de publish ejercitan el schema
        // de verdad en vez de un doble que siempre aprueba.
        PetSchemaValidator draftValidator =
                new PetSchemaValidator(new SchemaValidator(mapper), mapper);
        try {
            draftValidator.loadSchemas();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar el schema del ítem", e);
        }

        service = new CatalogIntegrationRequestServiceImpl(
                requestRepository, commercialDetailsRepository, designerDetailsRepository,
                catalogService, r2Service, mapper, commentRepository, draftValidator,
                keyTransactionRepository);
        ReflectionTestUtils.setField(service, "petsBucketName", PETS_BUCKET);
        ReflectionTestUtils.setField(service, "petsCdnDomain", "");
        ReflectionTestUtils.setField(service, "maxImageSizeBytes", MAX_SIZE);

        // CommercialDetails hereda el id de User vía @MapsId, así que id == userId.
        commercial = new CommercialDetails();
        commercial.setId(USER_ID);
        commercial.setCompanyName("Acme SAS");

        User designerUser = new User();
        designerUser.setId(DESIGNER_USER_ID);
        designer = new GameDesignerDetails();
        designer.setId(DESIGNER_USER_ID);
        designer.setUser(designerUser);
        designer.setName("Ana");
        designer.setLastName("Diseñadora");
        designer.setActive(true);
    }

    private void commercialExists() {
        when(commercialDetailsRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(commercial));
    }

    private CatalogIntegrationRequestDTO requestWithImage(String imageObjectKey) {
        return new CatalogIntegrationRequestDTO(
                "Galletas Acme", "Snack para mascotas", imageObjectKey, "Sube la energía");
    }

    @Nested
    @DisplayName("prepareImageUpload")
    class PrepareImageUpload {

        @Test
        @DisplayName("firma la subida contra el bucket de mascotas con la clave del comercial")
        void emitePermiso() {
            commercialExists();
            when(r2Service.generateUploadUrlInBucket(eq(PETS_BUCKET), any(), eq("image/png")))
                    .thenReturn(new FileUploadPermissionDTO("https://r2.example/put", 900L));

            PetImageUploadPermissionDTO permission = service.prepareImageUpload(
                    USER_ID, new PetImageUploadRequestDTO("image/png", "producto.png", 1024L));

            assertThat(permission.objectKey()).startsWith("catalog-requests/" + USER_ID + "/");
            assertThat(permission.uploadUrl()).isEqualTo("https://r2.example/put");
            assertThat(permission.expiresInSeconds()).isEqualTo(900L);

            ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
            verify(r2Service).generateUploadUrlInBucket(eq(PETS_BUCKET), key.capture(), eq("image/png"));
            assertThat(key.getValue()).isEqualTo(permission.objectKey());
        }

        @Test
        @DisplayName("rechaza archivos que superan el máximo configurado")
        void rechazaArchivoGrande() {
            commercialExists();

            assertThatThrownBy(() -> service.prepareImageUpload(
                    USER_ID, new PetImageUploadRequestDTO("image/png", "grande.png", MAX_SIZE + 1)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("muy grande");

            verify(r2Service, never()).generateUploadUrlInBucket(any(), any(), any());
        }

        @Test
        @DisplayName("rechaza tipos que no son imagen, aunque estén soportados en la plataforma")
        void rechazaPdf() {
            commercialExists();

            assertThatThrownBy(() -> service.prepareImageUpload(
                    USER_ID, new PetImageUploadRequestDTO("application/pdf", "ficha.pdf", 1024L)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("no soportado");

            verify(r2Service, never()).generateUploadUrlInBucket(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("submit")
    class Submit {

        @Test
        @DisplayName("valida contra R2 la imagen adjunta y la persiste")
        void aceptaImagenPropia() {
            commercialExists();
            String key = "catalog-requests/" + USER_ID + "/abc-123";
            when(r2Service.validateUploadedObjectInBucket(eq(PETS_BUCKET), eq(key), eq(MAX_SIZE), anySet()))
                    .thenReturn(SupportedMimeType.IMAGE_PNG);
            when(requestRepository.save(any(CatalogIntegrationRequest.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.submit(USER_ID, requestWithImage(key));

            ArgumentCaptor<CatalogIntegrationRequest> saved =
                    ArgumentCaptor.forClass(CatalogIntegrationRequest.class);
            verify(requestRepository).save(saved.capture());
            assertThat(saved.getValue().getImageObjectKey()).isEqualTo(key);
        }

        @Test
        @DisplayName("rechaza la clave de imagen de otro comercial")
        void rechazaImagenAjena() {
            commercialExists();

            assertThatThrownBy(() -> service.submit(USER_ID,
                    requestWithImage("catalog-requests/999/abc-123")))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("no corresponde a este comercial");

            verify(r2Service, never())
                    .validateUploadedObjectInBucket(any(), any(), anyLong(), anySet());
            verify(requestRepository, never()).save(any());
        }

        @Test
        @DisplayName("rechaza claves arbitrarias del bucket, como un sprite ya publicado")
        void rechazaClaveArbitraria() {
            commercialExists();

            assertThatThrownBy(() -> service.submit(USER_ID, requestWithImage("sprites/premium.png")))
                    .isInstanceOf(ValidationException.class);

            verify(requestRepository, never()).save(any());
        }

        @Test
        @DisplayName("rechaza path traversal dentro del prefijo propio")
        void rechazaTraversal() {
            commercialExists();

            assertThatThrownBy(() -> service.submit(USER_ID,
                    requestWithImage("catalog-requests/" + USER_ID + "/../999/robada.png")))
                    .isInstanceOf(ValidationException.class);

            verify(requestRepository, never()).save(any());
        }

        @Test
        @DisplayName("no acepta claves de otro bucket ni URLs completas")
        void rechazaUrlCompleta() {
            commercialExists();

            assertThatThrownBy(() -> service.submit(USER_ID,
                    requestWithImage("https://verygana-pets.r2.dev/catalog-requests/42/x")))
                    .isInstanceOf(ValidationException.class);

            verify(requestRepository, never()).save(any());
        }

        @Test
        @DisplayName("la imagen es opcional: sin clave no toca R2 y guarda null")
        void imagenOpcional() {
            commercialExists();
            when(requestRepository.save(any(CatalogIntegrationRequest.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.submit(USER_ID, requestWithImage("   "));

            verify(r2Service, never())
                    .validateUploadedObjectInBucket(any(), any(), anyLong(), anySet());

            ArgumentCaptor<CatalogIntegrationRequest> saved =
                    ArgumentCaptor.forClass(CatalogIntegrationRequest.class);
            verify(requestRepository).save(saved.capture());
            assertThat(saved.getValue().getImageObjectKey()).isNull();
        }
    }

    // ── helpers compartidos por los bloques de admin y diseñador ──────────────

    private CatalogIntegrationRequest request(CatalogRequestStatus status, String imageObjectKey) {
        CatalogIntegrationRequest r = new CatalogIntegrationRequest();
        r.setId(7L);
        r.setCommercial(commercial);
        r.setProductName("Galletas Acme");
        r.setDescription("Snack para mascotas");
        r.setImageObjectKey(imageObjectKey);
        r.setDesiredEffects("Sube la energía");
        r.setStatus(status);
        return r;
    }

    private void savePassthrough() {
        when(requestRepository.save(any(CatalogIntegrationRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("admin: aprueba asignando diseñador")
    class AdminFlow {

        @Test
        @DisplayName("aprobar asigna el diseñador y siembra el borrador con la imagen del comercial")
        void aprobarAsignaYSiembra() {
            String key = "catalog-requests/42/abc-123";
            when(requestRepository.findById(7L)).thenReturn(Optional.of(request(CatalogRequestStatus.PENDING, key)));
            when(designerDetailsRepository.findByUser_Id(DESIGNER_USER_ID)).thenReturn(Optional.of(designer));
            savePassthrough();

            var response = service.approve(7L, new ApprovePetRequestDTO(DESIGNER_USER_ID, "Va para el sprite"));

            assertThat(response.status()).isEqualTo(CatalogRequestStatus.APPROVED);
            assertThat(response.assignedDesignerUserId()).isEqualTo(DESIGNER_USER_ID);
            assertThat(response.assignedDesignerName()).isEqualTo("Ana Diseñadora");
            assertThat(response.adminNotes()).isEqualTo("Va para el sprite");
            assertThat(response.resultCatalogItemId()).isNull();
            assertThat(response.itemDraft())
                    .containsEntry("name", "Galletas Acme")
                    .containsEntry("spriteObjectKey", key)
                    .containsEntry("active", true);

            verify(catalogService, never()).createCatalogItem(any(), any());
        }

        @Test
        @DisplayName("no se puede asignar un diseñador inactivo")
        void diseñadorInactivo() {
            designer.setActive(false);
            when(requestRepository.findById(7L)).thenReturn(Optional.of(request(CatalogRequestStatus.PENDING, null)));
            when(designerDetailsRepository.findByUser_Id(DESIGNER_USER_ID)).thenReturn(Optional.of(designer));

            assertThatThrownBy(() -> service.approve(7L, new ApprovePetRequestDTO(DESIGNER_USER_ID, null)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("no está activo");

            verify(requestRepository, never()).save(any());
        }

        @Test
        @DisplayName("reasignar cambia el diseñador sin tocar el estado")
        void reasignar() {
            when(requestRepository.findById(7L)).thenReturn(Optional.of(request(CatalogRequestStatus.APPROVED, null)));
            when(designerDetailsRepository.findByUser_Id(DESIGNER_USER_ID)).thenReturn(Optional.of(designer));
            savePassthrough();

            var response = service.assignDesigner(7L, DESIGNER_USER_ID);

            assertThat(response.assignedDesignerUserId()).isEqualTo(DESIGNER_USER_ID);
            assertThat(response.status()).isEqualTo(CatalogRequestStatus.APPROVED);
        }
    }

    @Nested
    @DisplayName("diseñador: solo lo que tiene asignado")
    class DesignerFlow {

        private void assignedToMe(CatalogIntegrationRequest r) {
            r.setAssignedDesigner(designer);
            when(requestRepository.findByIdAndAssignedDesigner_User_Id(7L, DESIGNER_USER_ID))
                    .thenReturn(Optional.of(r));
        }

        @Test
        @DisplayName("una solicitud no asignada a él no existe")
        void noAsignadaNoExiste() {
            when(requestRepository.findByIdAndAssignedDesigner_User_Id(7L, DESIGNER_USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.saveItemDraft(7L, DESIGNER_USER_ID, Map.of("name", "X")))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("no asignada a este diseñador");

            verify(requestRepository, never()).save(any());
        }

        @Test
        @DisplayName("la bandeja solo trae lo asignado")
        void bandeja() {
            when(requestRepository.findByAssignedDesigner_User_IdOrderByCreatedAtDesc(DESIGNER_USER_ID))
                    .thenReturn(List.of(request(CatalogRequestStatus.APPROVED, null)));

            assertThat(service.getAssignedRequests(DESIGNER_USER_ID)).hasSize(1);
            verify(requestRepository, never()).findAllByOrderByCreatedAtDesc();
        }

        @Test
        @DisplayName("guardar borrador pasa la solicitud a ITEM_IN_PROGRESS")
        void guardarBorrador() {
            assignedToMe(request(CatalogRequestStatus.APPROVED, null));
            savePassthrough();

            var response = service.saveItemDraft(7L, DESIGNER_USER_ID, Map.of("name", "Galletas XL", "price", 500));

            assertThat(response.status()).isEqualTo(CatalogRequestStatus.ITEM_IN_PROGRESS);
            assertThat(response.itemDraft()).containsEntry("price", 500);
        }

        @Test
        @DisplayName("no se puede armar el ítem antes de que el admin apruebe")
        void borradorAntesDeAprobar() {
            assignedToMe(request(CatalogRequestStatus.PENDING, null));

            assertThatThrownBy(() -> service.saveItemDraft(7L, DESIGNER_USER_ID, Map.of("name", "X")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("aprobar la solicitud");

            verify(requestRepository, never()).save(any());
        }

        @Test
        @DisplayName("publicar crea el ítem con el sprite y cierra la solicitud")
        void publicar() {
            String key = "catalog-requests/42/abc-123";
            CatalogIntegrationRequest r = request(CatalogRequestStatus.ITEM_IN_PROGRESS, key);
            r.setItemDraft(new java.util.HashMap<>(Map.of(
                    "externalId", 1000,
                    "name", "Galletas Acme",
                    "price", 500,
                    "healthDelta", 10,
                    "spriteObjectKey", key)));
            assignedToMe(r);
            savePassthrough();

            when(catalogService.createCatalogItem(any(PetCatalogItemRequestDTO.class), eq(key)))
                    .thenReturn(new PetCatalogItemResponseDTO(99L, null, "Galletas Acme", null,
                            null, null, null, 500, "https://cdn/x.png", null, 10,
                            null, null, null, null, null, null, true));

            var response = service.publishCatalogItem(7L, DESIGNER_USER_ID);

            assertThat(response.status()).isEqualTo(CatalogRequestStatus.COMPLETED);
            assertThat(response.resultCatalogItemId()).isEqualTo(99L);

            ArgumentCaptor<PetCatalogItemRequestDTO> dto =
                    ArgumentCaptor.forClass(PetCatalogItemRequestDTO.class);
            verify(catalogService).createCatalogItem(dto.capture(), eq(key));
            assertThat(dto.getValue().name()).isEqualTo("Galletas Acme");
            assertThat(dto.getValue().price()).isEqualTo(500);
        }

        @Test
        @DisplayName("no publica un borrador vacío")
        void publicarSinBorrador() {
            assignedToMe(request(CatalogRequestStatus.APPROVED, null));

            assertThatThrownBy(() -> service.publishCatalogItem(7L, DESIGNER_USER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No hay borrador");
        }

        @Test
        @DisplayName("no publica sin nombre")
        void publicarSinNombre() {
            CatalogIntegrationRequest r = request(CatalogRequestStatus.ITEM_IN_PROGRESS, null);
            r.setItemDraft(new java.util.HashMap<>(Map.of("externalId", 1000, "price", 500)));
            assignedToMe(r);

            assertThatThrownBy(() -> service.publishCatalogItem(7L, DESIGNER_USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name");

            verify(catalogService, never()).createCatalogItem(any(), any());
        }

        /**
         * El caso que motivó el schema: un ítem sin externalId se publicaba igual y
         * quedaba invisible para el juego (el catálogo omite los que no lo tienen).
         */
        @Test
        @DisplayName("no publica sin externalId: quedaría invisible en el juego")
        void publicarSinExternalId() {
            CatalogIntegrationRequest r = request(CatalogRequestStatus.ITEM_IN_PROGRESS, null);
            r.setItemDraft(new java.util.HashMap<>(Map.of("name", "Galletas", "price", 500)));
            assignedToMe(r);

            assertThatThrownBy(() -> service.publishCatalogItem(7L, DESIGNER_USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("externalId");

            verify(catalogService, never()).createCatalogItem(any(), any());
        }

        @Test
        @DisplayName("una solicitud ya publicada no se puede volver a tocar")
        void completadaEsFinal() {
            assignedToMe(request(CatalogRequestStatus.COMPLETED, null));

            assertThatThrownBy(() -> service.publishCatalogItem(7L, DESIGNER_USER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ya fue finalizada");
        }
    }
}

