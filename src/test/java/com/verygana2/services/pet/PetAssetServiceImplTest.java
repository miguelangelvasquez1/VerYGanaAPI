package com.verygana2.services.pet;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.pet.PetAssetUploadRequestDTO;
import com.verygana2.dtos.pet.PetImageUploadPermissionDTO;
import com.verygana2.models.enums.PetAssetKind;
import com.verygana2.models.userDetails.GameDesignerDetails;
import com.verygana2.repositories.details.GameDesignerDetailsRepository;
import com.verygana2.storage.service.R2Service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PetAssetServiceImpl}: los formatos que acepta cada destino y la
 * forma de la clave que se genera.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PetAssetServiceImpl")
class PetAssetServiceImplTest {

    private static final String BUCKET = "verygana-pets";
    private static final long MAX_SIZE = 25L * 1024 * 1024;
    private static final Long DESIGNER_USER_ID = 6L;

    @Mock private GameDesignerDetailsRepository designerDetailsRepository;
    @Mock private R2Service r2Service;

    private PetAssetServiceImpl service;

    @BeforeEach
    void setUp() {
        // Resolver real: el permiso ahora lleva la url pública y es parte de lo
        // que se comprueba, así que un mock solo la reimplementaría.
        PetAssetUrlResolver urlResolver = new PetAssetUrlResolver();
        ReflectionTestUtils.setField(urlResolver, "petsCdnDomain", "");
        ReflectionTestUtils.setField(urlResolver, "petsBucketName", BUCKET);

        service = new PetAssetServiceImpl(designerDetailsRepository, r2Service, urlResolver);
        ReflectionTestUtils.setField(service, "petsBucketName", BUCKET);
        ReflectionTestUtils.setField(service, "maxAssetSizeBytes", MAX_SIZE);
    }

    private void designerExists() {
        GameDesignerDetails designer = new GameDesignerDetails();
        designer.setId(DESIGNER_USER_ID);
        when(designerDetailsRepository.findByUser_Id(DESIGNER_USER_ID)).thenReturn(Optional.of(designer));
    }

    private void permissionGranted() {
        when(r2Service.generateUploadUrlInBucket(eq(BUCKET), anyString(), anyString()))
                .thenAnswer(i -> FileUploadPermissionDTO.builder()
                        .uploadUrl("https://r2/upload")
                        .expiresInSeconds(900L)
                        .build());
    }

    private static PetAssetUploadRequestDTO request(PetAssetKind kind, String contentType) {
        return new PetAssetUploadRequestDTO(kind, contentType, "asset.bin", 1024L);
    }

    @Test
    @DisplayName("sprite de catálogo: la clave lleva la carpeta y el id del diseñador")
    void catalogSprite_keyHasPrefixAndDesignerId() {
        designerExists();
        permissionGranted();

        PetImageUploadPermissionDTO result =
                service.prepareUpload(DESIGNER_USER_ID, request(PetAssetKind.CATALOG_SPRITE, "image/png"));

        assertThat(result.objectKey()).startsWith("catalog-sprites/" + DESIGNER_USER_ID + "/");
        assertThat(result.uploadUrl()).isEqualTo("https://r2/upload");
    }

    @Test
    @DisplayName("objeto de escena: usa su propia carpeta")
    void sceneObject_usesOwnPrefix() {
        designerExists();
        permissionGranted();

        PetImageUploadPermissionDTO result =
                service.prepareUpload(DESIGNER_USER_ID, request(PetAssetKind.SCENE_OBJECT, "image/png"));

        assertThat(result.objectKey()).startsWith("scene-objects/" + DESIGNER_USER_ID + "/");
    }

    /** El build tiene SpawnVideoDelayed, así que las escenas aceptan video. */
    @Test
    @DisplayName("objeto de escena acepta video")
    void sceneObject_allowsVideo() {
        designerExists();
        permissionGranted();

        assertThat(service.prepareUpload(DESIGNER_USER_ID, request(PetAssetKind.SCENE_OBJECT, "video/mp4")))
                .isNotNull();
    }

    /** Un sprite es una textura del catálogo: un video ahí no tendría sentido. */
    @Test
    @DisplayName("sprite de catálogo rechaza video")
    void catalogSprite_rejectsVideo() {
        designerExists();

        assertThatThrownBy(() ->
                service.prepareUpload(DESIGNER_USER_ID, request(PetAssetKind.CATALOG_SPRITE, "video/mp4")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Formato no soportado");

        verify(r2Service, never()).generateUploadUrlInBucket(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("formato desconocido se rechaza sin pedirle nada a R2")
    void unknownMime_rejected() {
        designerExists();

        assertThatThrownBy(() ->
                service.prepareUpload(DESIGNER_USER_ID, request(PetAssetKind.SCENE_OBJECT, "application/zip")))
                .isInstanceOf(ValidationException.class);

        verify(r2Service, never()).generateUploadUrlInBucket(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("archivo más grande que el máximo se rechaza")
    void oversizedFile_rejected() {
        designerExists();

        PetAssetUploadRequestDTO tooBig = new PetAssetUploadRequestDTO(
                PetAssetKind.SCENE_OBJECT, "video/mp4", "grande.mp4", MAX_SIZE + 1);

        assertThatThrownBy(() -> service.prepareUpload(DESIGNER_USER_ID, tooBig))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("muy grande");
    }

    @Test
    @DisplayName("diseñador inexistente")
    void unknownDesigner_throws() {
        when(designerDetailsRepository.findByUser_Id(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.prepareUpload(99L, request(PetAssetKind.CATALOG_SPRITE, "image/png")))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("cada subida genera una clave distinta")
    void keysAreUnique() {
        designerExists();
        permissionGranted();

        String a = service.prepareUpload(DESIGNER_USER_ID,
                request(PetAssetKind.SCENE_OBJECT, "image/png")).objectKey();
        String b = service.prepareUpload(DESIGNER_USER_ID,
                request(PetAssetKind.SCENE_OBJECT, "image/png")).objectKey();

        assertThat(a).isNotEqualTo(b);
    }
}
