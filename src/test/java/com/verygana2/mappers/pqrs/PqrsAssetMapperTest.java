package com.verygana2.mappers.pqrs;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.dtos.pqrs.responses.PqrsAssetResponseDTO;
import com.verygana2.models.enums.MediaType;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.enums.pqrs.PqrsAssetStatus;
import com.verygana2.models.pqrs.PqrsAsset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del mapper MapStruct de evidencia de PQRS
 * ({@link PqrsAssetMapperImpl}, generado en target/generated-sources a
 * partir de {@link PqrsAssetMapper}). Se instancia la clase generada
 * directamente (sin contexto de Spring) y se inyecta {@code appBaseUrl} por
 * reflexión, igual que {@code PqrsMapperTest} hace con su campo heredado —
 * salvo que aquí el campo viene con {@code @Value} en la clase abstracta
 * base, así que se usa {@link ReflectionTestUtils} en vez de acceso directo.
 */
@DisplayName("PqrsAssetMapper")
class PqrsAssetMapperTest {

    private PqrsAssetMapperImpl mapper;

    @BeforeEach
    void setUp() {
        mapper = new PqrsAssetMapperImpl();
        ReflectionTestUtils.setField(mapper, "appBaseUrl", "https://api.verygana.test");
    }

    private PqrsAsset sampleAsset() {
        return PqrsAsset.builder()
                .id(42L)
                .ownerUserId(1L)
                .objectKey("pqrs/assets/42/evidence.png")
                .originalFileName("evidencia.png")
                .sizeBytes(2048L)
                .mimeType(SupportedMimeType.IMAGE_PNG)
                .mediaType(MediaType.IMAGE)
                .status(PqrsAssetStatus.VALIDATED)
                .createdAt(ZonedDateTime.now())
                .build();
    }

    @Test
    @DisplayName("toResponseDTO: arma viewUrl como appBaseUrl + /pqrs/assets/{id}/view")
    void toResponseDTO_buildsViewUrlFromAppBaseUrlAndAssetId() {
        PqrsAsset asset = sampleAsset();

        PqrsAssetResponseDTO dto = mapper.toResponseDTO(asset);

        assertThat(dto.getViewUrl()).isEqualTo("https://api.verygana.test/pqrs/assets/42/view");
    }

    @Test
    @DisplayName("toResponseDTO con null: retorna null (comportamiento estándar de MapStruct)")
    void toResponseDTO_nullInput_returnsNull() {
        assertThat(mapper.toResponseDTO(null)).isNull();
    }

    @Test
    @DisplayName("toResponseDTO: copia id, originalFileName, mediaType, mimeType, sizeBytes y createdAt")
    void toResponseDTO_mapsFlatFieldsDirectly() {
        PqrsAsset asset = sampleAsset();

        PqrsAssetResponseDTO dto = mapper.toResponseDTO(asset);

        assertThat(dto.getId()).isEqualTo(42L);
        assertThat(dto.getOriginalFileName()).isEqualTo("evidencia.png");
        assertThat(dto.getMediaType()).isEqualTo(MediaType.IMAGE);
        assertThat(dto.getMimeType()).isEqualTo(SupportedMimeType.IMAGE_PNG);
        assertThat(dto.getSizeBytes()).isEqualTo(2048L);
        assertThat(dto.getCreatedAt()).isEqualTo(asset.getCreatedAt());
    }
}
