package com.verygana2.services.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.branding.ConfirmCorporateResourceDTO;
import com.verygana2.exceptions.StorageException;
import com.verygana2.models.branding.BrandingRequest;
import com.verygana2.models.branding.CorporateResource;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.BrandingRequestStatus;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.repositories.branding.CorporateResourceRepository;
import com.verygana2.storage.service.AssetOrphanedService;
import com.verygana2.storage.service.R2Service;

import jakarta.validation.ValidationException;

/**
 * Archivos de recursos corporativos de una solicitud de branding: se suben con
 * {@code isPrivate=true} (o sea a {@code private/<key>}), así que hay que borrarlos con ese
 * prefijo, y las confirmaciones fallidas deben quedar marcadas aunque la transacción se revierta.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BrandingRequestServiceImpl — archivos de recursos corporativos")
class BrandingRequestServiceImplResourcesTest {

    private static final Long REQUEST_ID = 5L;
    private static final Long USER_ID = 11L;

    @Mock private BrandingRequestRepository brandingRequestRepository;
    @Mock private CorporateResourceRepository corporateResourceRepository;
    @Mock private R2Service r2Service;
    @Mock private AssetOrphanedService assetOrphanedService;

    @InjectMocks private BrandingRequestServiceImpl service;

    private CorporateResource resource(long id, String key, AssetStatus status) {
        return CorporateResource.builder().id(id).objectKey(key).sizeBytes(1024L).status(status).build();
    }

    private BrandingRequest draftRequestWith(List<CorporateResource> resources) {
        BrandingRequest request = BrandingRequest.builder()
                .id(REQUEST_ID)
                .status(BrandingRequestStatus.DRAFT)
                .corporateResources(new ArrayList<>(resources))
                .build();
        when(brandingRequestRepository.findByIdAndCommercialUserId(REQUEST_ID, USER_ID))
                .thenReturn(Optional.of(request));
        return request;
    }

    @Test
    @DisplayName("cancelar un borrador borra los archivos en private/<key> (antes: sin prefijo, no borraba nada) y luego las filas")
    void cancel_deletesFilesUnderPrivatePrefix() {
        CorporateResource a = resource(1L, "branding/5/resources/a.png", AssetStatus.VALIDATED);
        CorporateResource b = resource(2L, "branding/5/resources/b.png", AssetStatus.PENDING);
        BrandingRequest request = draftRequestWith(List.of(a, b));

        service.cancelBrandingRequest(REQUEST_ID, USER_ID);

        // deletePrivateObjects recibe las keys como están en las filas y R2Service les añade "private/".
        verify(r2Service).deletePrivateObjects(List.of("branding/5/resources/a.png", "branding/5/resources/b.png"));
        verify(r2Service, never()).deleteObjects(any());
        assertThat(request.getStatus()).isEqualTo(BrandingRequestStatus.CANCELLED);

        // R2 va al final: si falla, se revierte toda la cancelación en vez de perder las filas.
        InOrder order = inOrder(corporateResourceRepository, r2Service);
        order.verify(corporateResourceRepository).deleteAll(any());
        order.verify(r2Service).deletePrivateObjects(any());
    }

    @Test
    @DisplayName("si R2 no puede borrar los archivos la cancelación falla (no queda a medias con las filas borradas)")
    void cancel_r2Failure_propagates() {
        draftRequestWith(List.of(resource(1L, "branding/5/resources/a.png", AssetStatus.VALIDATED)));
        doThrow(new StorageException("R2 down")).when(r2Service).deletePrivateObjects(any());

        assertThatThrownBy(() -> service.cancelBrandingRequest(REQUEST_ID, USER_ID))
                .isInstanceOf(StorageException.class);
    }

    @Test
    @DisplayName("un borrador sin recursos se cancela sin tocar filas de recursos")
    void cancel_withoutResources() {
        BrandingRequest request = draftRequestWith(List.of());

        service.cancelBrandingRequest(REQUEST_ID, USER_ID);

        verify(corporateResourceRepository, never()).deleteAll(any());
        assertThat(request.getStatus()).isEqualTo(BrandingRequestStatus.CANCELLED);
    }

    @Test
    @DisplayName("confirmación fallida: el recurso se orfana por AssetOrphanedService (transacción propia) y se relanza la excepción")
    void confirmFailure_orphansThroughAssetOrphanedService() {
        CorporateResource pending = resource(7L, "branding/5/resources/x.png", AssetStatus.PENDING);
        draftRequestWith(List.of(pending));
        when(corporateResourceRepository.findByIdAndBrandingRequest_Id(7L, REQUEST_ID)).thenReturn(Optional.of(pending));
        when(r2Service.validateUploadedObject(eq(true), anyString(), anyLong(), anyLong(), anySet()))
                .thenThrow(new ValidationException("Content-Type real inválido"));

        ConfirmCorporateResourceDTO confirm = new ConfirmCorporateResourceDTO();
        confirm.setResourceId(7L);

        assertThatThrownBy(() -> service.confirmCorporateResource(REQUEST_ID, confirm, USER_ID))
                .isInstanceOf(ValidationException.class);

        verify(assetOrphanedService).markCorporateResourceAsOrphaned(7L);
        // Marcarlo aquí mismo se perdería al revertirse la transacción de este método.
        verify(corporateResourceRepository, never()).save(any());
        assertThat(pending.getStatus()).isEqualTo(AssetStatus.PENDING);
    }
}
