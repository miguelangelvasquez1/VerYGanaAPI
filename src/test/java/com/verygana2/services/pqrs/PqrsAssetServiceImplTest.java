package com.verygana2.services.pqrs;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.config.pqrs.PqrsAssetProperties;
import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.pqrs.requests.PreparePqrsAssetRequestDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAssetResponseDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAssetUploadPermissionDTO;
import com.verygana2.exceptions.pqrsExceptions.PqrsAccessDeniedException;
import com.verygana2.mappers.pqrs.PqrsAssetMapper;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.enums.pqrs.PqrsAssetStatus;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.models.pqrs.PqrsAsset;
import com.verygana2.repositories.pqrs.PqrsAssetRepository;
import com.verygana2.storage.service.R2Service;

import jakarta.validation.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PqrsAssetServiceImpl}: ciclo de vida de la evidencia
 * (foto/video) opcional que un usuario puede adjuntar a un PQRS. El caso más
 * importante es {@code validateAndClaimAssets} con lista null/vacía — es la
 * garantía de que la evidencia es opcional, no un requisito.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PqrsAssetServiceImpl")
class PqrsAssetServiceImplTest {

    @Mock private PqrsAssetRepository pqrsAssetRepository;
    @Mock private PqrsAssetMapper pqrsAssetMapper;
    @Mock private PqrsAssetProperties pqrsAssetProperties;
    @Mock private R2Service r2Service;

    private PqrsAssetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PqrsAssetServiceImpl(pqrsAssetRepository, pqrsAssetMapper, pqrsAssetProperties, r2Service);
    }

    private PqrsAsset asset(Long id, Long ownerUserId, PqrsAssetStatus status, Pqrs pqrs) {
        return PqrsAsset.builder()
                .id(id)
                .ownerUserId(ownerUserId)
                .objectKey("pqrs-evidence/" + ownerUserId + "/" + id)
                .sizeBytes(1000L)
                .status(status)
                .pqrs(pqrs)
                .build();
    }

    @Nested
    @DisplayName("prepareUpload")
    class PrepareUpload {

        @Test
        @DisplayName("archivo válido: persiste PENDING y devuelve la URL prefirmada")
        void validFile_persistsPendingAndReturnsUploadUrl() {
            PreparePqrsAssetRequestDTO dto = new PreparePqrsAssetRequestDTO();
            dto.setOriginalFileName("evidencia.jpg");
            dto.setContentType("image/jpeg");
            dto.setSizeBytes(1000L);

            when(pqrsAssetProperties.getMaxSizeBytes()).thenReturn(26_214_400L);
            when(pqrsAssetRepository.save(any(PqrsAsset.class))).thenAnswer(inv -> {
                PqrsAsset a = inv.getArgument(0);
                a.setId(1L);
                return a;
            });
            when(r2Service.generateUploadUrl(eq(true), any(), eq("image/jpeg")))
                    .thenReturn(new FileUploadPermissionDTO("https://upload-url", 900L));

            PqrsAssetUploadPermissionDTO result = service.prepareUpload(1L, dto);

            assertThat(result.getAssetId()).isEqualTo(1L);
            assertThat(result.getPermission().getUploadUrl()).isEqualTo("https://upload-url");
        }

        @Test
        @DisplayName("archivo demasiado grande: lanza ValidationException y no persiste nada")
        void oversizedFile_throwsValidationException() {
            PreparePqrsAssetRequestDTO dto = new PreparePqrsAssetRequestDTO();
            dto.setOriginalFileName("video.mp4");
            dto.setContentType("video/mp4");
            dto.setSizeBytes(50_000_000L);

            when(pqrsAssetProperties.getMaxSizeBytes()).thenReturn(26_214_400L);

            assertThatThrownBy(() -> service.prepareUpload(1L, dto))
                    .isInstanceOf(ValidationException.class);

            verifyNoInteractions(pqrsAssetRepository, r2Service);
        }

        @Test
        @DisplayName("tipo de archivo no soportado: lanza ValidationException")
        void unsupportedMimeType_throwsValidationException() {
            PreparePqrsAssetRequestDTO dto = new PreparePqrsAssetRequestDTO();
            dto.setOriginalFileName("doc.pdf");
            dto.setContentType("application/pdf");
            dto.setSizeBytes(1000L);

            when(pqrsAssetProperties.getMaxSizeBytes()).thenReturn(26_214_400L);

            assertThatThrownBy(() -> service.prepareUpload(1L, dto))
                    .isInstanceOf(ValidationException.class);

            verifyNoInteractions(pqrsAssetRepository, r2Service);
        }
    }

    @Nested
    @DisplayName("confirmUpload")
    class ConfirmUpload {

        @Test
        @DisplayName("subida válida: pasa a VALIDATED y delega el mapeo al mapper (viewUrl es una ruta propia del backend, no de R2)")
        void validUpload_marksValidatedAndDelegatesMapping() {
            PqrsAsset pending = asset(1L, 1L, PqrsAssetStatus.PENDING, null);
            PqrsAssetResponseDTO mapped = new PqrsAssetResponseDTO();
            mapped.setViewUrl("/pqrs/assets/1/view");

            when(pqrsAssetRepository.findById(1L)).thenReturn(Optional.of(pending));
            when(r2Service.validateUploadedObject(eq(true), eq(pending.getObjectKey()), eq(1000L), anyLong(), anySet()))
                    .thenReturn(SupportedMimeType.IMAGE_JPEG);
            when(pqrsAssetMapper.toResponseDTO(pending)).thenReturn(mapped);

            PqrsAssetResponseDTO result = service.confirmUpload(1L, 1L);

            assertThat(pending.getStatus()).isEqualTo(PqrsAssetStatus.VALIDATED);
            assertThat(pending.getMimeType()).isEqualTo(SupportedMimeType.IMAGE_JPEG);
            assertThat(result.getViewUrl()).isEqualTo("/pqrs/assets/1/view");
            // r2Service ya no se usa para generar viewUrl — solo para validar el objeto subido.
            verify(r2Service, never()).getPrivateObject(any(), anyInt());
        }

        @Test
        @DisplayName("dueño incorrecto: lanza PqrsAccessDeniedException")
        void wrongOwner_throwsAccessDenied() {
            PqrsAsset pending = asset(1L, 1L, PqrsAssetStatus.PENDING, null);
            when(pqrsAssetRepository.findById(1L)).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> service.confirmUpload(2L, 1L))
                    .isInstanceOf(PqrsAccessDeniedException.class);

            verifyNoInteractions(r2Service);
        }

        @Test
        @DisplayName("ya confirmado: lanza ValidationException")
        void alreadyValidated_throwsValidationException() {
            PqrsAsset validated = asset(1L, 1L, PqrsAssetStatus.VALIDATED, null);
            when(pqrsAssetRepository.findById(1L)).thenReturn(Optional.of(validated));

            assertThatThrownBy(() -> service.confirmUpload(1L, 1L))
                    .isInstanceOf(ValidationException.class);

            verifyNoInteractions(r2Service);
        }
    }

    @Nested
    @DisplayName("validateAndClaimAssets")
    class ValidateAndClaimAssets {

        @Test
        @DisplayName("lista null: no-op, retorna vacío sin tocar el repositorio")
        void nullList_isNoOp() {
            List<PqrsAsset> result = service.validateAndClaimAssets(null, 1L, new Pqrs());

            assertThat(result).isEmpty();
            verifyNoInteractions(pqrsAssetRepository);
        }

        @Test
        @DisplayName("lista vacía: no-op, retorna vacío sin tocar el repositorio")
        void emptyList_isNoOp() {
            List<PqrsAsset> result = service.validateAndClaimAssets(List.of(), 1L, new Pqrs());

            assertThat(result).isEmpty();
            verifyNoInteractions(pqrsAssetRepository);
        }

        @Test
        @DisplayName("assets válidos, propios y confirmados: los reclama para el Pqrs y los persiste")
        void validOwnedConfirmedAssets_claimsAndPersists() {
            Pqrs pqrs = new Pqrs();
            PqrsAsset a1 = asset(1L, 9L, PqrsAssetStatus.VALIDATED, null);
            PqrsAsset a2 = asset(2L, 9L, PqrsAssetStatus.VALIDATED, null);

            when(pqrsAssetProperties.getMaxCount()).thenReturn(5);
            when(pqrsAssetRepository.findAllByIdIn(List.of(1L, 2L))).thenReturn(List.of(a1, a2));

            List<PqrsAsset> result = service.validateAndClaimAssets(List.of(1L, 2L), 9L, pqrs);

            assertThat(result).containsExactlyInAnyOrder(a1, a2);
            assertThat(a1.getPqrs()).isSameAs(pqrs);
            assertThat(a2.getPqrs()).isSameAs(pqrs);
            verify(pqrsAssetRepository).saveAll(List.of(a1, a2));
        }

        @Test
        @DisplayName("un asset pertenece a otro usuario: lanza PqrsAccessDeniedException y no reclama ninguno")
        void assetOwnedByAnotherUser_throwsAccessDeniedAndClaimsNothing() {
            Pqrs pqrs = new Pqrs();
            PqrsAsset ownedByOther = asset(1L, 123L, PqrsAssetStatus.VALIDATED, null);

            when(pqrsAssetProperties.getMaxCount()).thenReturn(5);
            when(pqrsAssetRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(ownedByOther));

            assertThatThrownBy(() -> service.validateAndClaimAssets(List.of(1L), 9L, pqrs))
                    .isInstanceOf(PqrsAccessDeniedException.class);

            verify(pqrsAssetRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("un asset todavía PENDING (no confirmado): lanza ValidationException")
        void assetStillPending_throwsValidationException() {
            Pqrs pqrs = new Pqrs();
            PqrsAsset stillPending = asset(1L, 9L, PqrsAssetStatus.PENDING, null);

            when(pqrsAssetProperties.getMaxCount()).thenReturn(5);
            when(pqrsAssetRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(stillPending));

            assertThatThrownBy(() -> service.validateAndClaimAssets(List.of(1L), 9L, pqrs))
                    .isInstanceOf(ValidationException.class);

            verify(pqrsAssetRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("un asset ya reclamado por otro Pqrs: lanza ValidationException")
        void assetAlreadyClaimedByAnotherPqrs_throwsValidationException() {
            Pqrs otherPqrs = new Pqrs();
            Pqrs pqrs = new Pqrs();
            PqrsAsset alreadyClaimed = asset(1L, 9L, PqrsAssetStatus.VALIDATED, otherPqrs);

            when(pqrsAssetProperties.getMaxCount()).thenReturn(5);
            when(pqrsAssetRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(alreadyClaimed));

            assertThatThrownBy(() -> service.validateAndClaimAssets(List.of(1L), 9L, pqrs))
                    .isInstanceOf(ValidationException.class);

            verify(pqrsAssetRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("supera el máximo permitido: lanza ValidationException sin consultar el repositorio")
        void exceedsMaxCount_throwsValidationException() {
            when(pqrsAssetProperties.getMaxCount()).thenReturn(2);

            assertThatThrownBy(() -> service.validateAndClaimAssets(List.of(1L, 2L, 3L), 9L, new Pqrs()))
                    .isInstanceOf(ValidationException.class);

            verifyNoInteractions(pqrsAssetRepository);
        }

        @Test
        @DisplayName("id inexistente: lanza ValidationException")
        void unknownId_throwsValidationException() {
            when(pqrsAssetProperties.getMaxCount()).thenReturn(5);
            when(pqrsAssetRepository.findAllByIdIn(List.of(404L))).thenReturn(List.of());

            assertThatThrownBy(() -> service.validateAndClaimAssets(List.of(404L), 9L, new Pqrs()))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    @DisplayName("streamAsset")
    class StreamAsset {

        @Test
        @DisplayName("usuario que no es dueño ni admin: lanza PqrsAccessDeniedException antes de tocar R2")
        void notOwnerNotAdmin_throwsAccessDeniedWithoutTouchingR2() {
            PqrsAsset asset = asset(1L, 9L, PqrsAssetStatus.VALIDATED, null);
            when(pqrsAssetRepository.findById(1L)).thenReturn(Optional.of(asset));

            jakarta.servlet.http.HttpServletResponse response = org.mockito.Mockito.mock(
                    jakarta.servlet.http.HttpServletResponse.class);

            assertThatThrownBy(() -> service.streamAsset(1L, 2L, false, response))
                    .isInstanceOf(PqrsAccessDeniedException.class);

            verifyNoInteractions(r2Service);
        }

        @Test
        @DisplayName("id inexistente: lanza excepción de no encontrado")
        void unknownAsset_throwsNotFound() {
            when(pqrsAssetRepository.findById(404L)).thenReturn(Optional.empty());

            jakarta.servlet.http.HttpServletResponse response = org.mockito.Mockito.mock(
                    jakarta.servlet.http.HttpServletResponse.class);

            assertThatThrownBy(() -> service.streamAsset(404L, 9L, false, response))
                    .isInstanceOf(org.hibernate.ObjectNotFoundException.class);
        }
    }
}
