package com.verygana2.services.gameDesigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.models.branding.Asset;
import com.verygana2.repositories.games.AssetRepository;
import com.verygana2.storage.service.R2Service;

/**
 * El objectKey de un asset del diseñador tiene que terminar en la extensión del
 * archivo.
 *
 * Los builds de Unity resuelven el AudioType a partir de la extensión de la URL
 * ({@code UnityWebRequestMultimedia.GetAudioClip} exige el tipo por adelantado y no
 * lo infiere del Content-Type). Con una clave sin extensión el clip no carga, el
 * juego cae al sonido por defecto y no reporta ningún error: el brandeo se ve bien
 * y suena mal.
 *
 * Verificado en dash-runner el 2026-08-31: la misma URL, mismos bytes y mismo
 * Content-Type funcionaba con «.mp3» al final y fallaba sin él.
 */
@ExtendWith(MockitoExtension.class)
class DesignerAssetObjectKeyTest {

    @Mock private R2Service r2Service;
    @Mock private AssetRepository assetRepository;

    @InjectMocks private GameDesignerServiceImpl service;

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "audio/mpeg, .mp3",
        "audio/ogg,  .ogg",
        "audio/wav,  .wav",
        "image/png,  .png",
        "image/jpeg, .jpg",
        "image/webp, .webp",
    })
    @DisplayName("el objectKey del asset termina en la extensión que corresponde al content-type")
    void objectKeyCarriesTheExtension(String contentType, String expectedExtension) {
        when(r2Service.buildPublicUrl(anyString())).thenReturn("https://cdn.test/x");
        when(assetRepository.save(any(Asset.class)))
            .thenAnswer(inv -> {
                Asset a = inv.getArgument(0);
                a.setId(1L);
                return a;
            });

        FileUploadRequestDTO request = new FileUploadRequestDTO();
        request.setOriginalFileName("sonido-de-marca");
        request.setContentType(contentType);
        request.setSizeBytes(1024L);

        service.generateUploadUrl(request, 4L);

        ArgumentCaptor<String> uploadKey = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(r2Service)
            .generateUploadUrl(anyBoolean(), uploadKey.capture(), anyString());

        ArgumentCaptor<Asset> saved = ArgumentCaptor.forClass(Asset.class);
        org.mockito.Mockito.verify(assetRepository).save(saved.capture());

        // La clave firmada para subir y la que queda persistida deben ser la misma,
        // porque la URL pública se construye a partir del objectKey guardado.
        assertThat(uploadKey.getValue()).endsWith(expectedExtension);
        assertThat(saved.getValue().getObjectKey())
            .endsWith(expectedExtension)
            .isEqualTo(uploadKey.getValue());
    }
}
