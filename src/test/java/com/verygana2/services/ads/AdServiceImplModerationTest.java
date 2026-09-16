package com.verygana2.services.ads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.verygana2.dtos.ad.responses.AdResponseDTO;
import com.verygana2.exceptions.adsExceptions.InvalidAdStateException;
import com.verygana2.mappers.AdMapper;
import com.verygana2.models.User;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.ads.AdAsset;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.AdRepository;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.storage.service.R2Service;

/**
 * Verifica que el ciclo de moderación de anuncios mantenga coherente la
 * visibilidad del asset en R2:
 *
 * <ul>
 *   <li>al bloquear se revierte el objeto a privado (deja de servirse por el CDN),</li>
 *   <li>al reactivar/pausar desde BLOCKED se vuelve a publicar,</li>
 *   <li>transiciones que no salen de BLOCKED no tocan el storage,</li>
 *   <li>un anuncio BLOCKED expone su contenido sólo por URL prefirmada, nunca la pública.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdServiceImpl — moderación y visibilidad del asset en R2")
class AdServiceImplModerationTest {

    private static final Long AD_ID = 501L;
    private static final String OBJECT_KEY = "commercials/7/ad-501.jpg";
    private static final Instant FIXED = Instant.parse("2026-01-01T00:00:00Z");

    @Mock AdRepository adRepository;
    @Mock AdMapper adMapper;
    @Mock R2Service r2Service;
    @Mock NotificationService notificationService;
    @Mock Clock clock;

    @InjectMocks AdServiceImpl adServiceImpl;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(FIXED);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(adRepository.save(any(Ad.class))).thenAnswer(inv -> inv.getArgument(0));
        when(adMapper.toDto(any(Ad.class))).thenReturn(new AdResponseDTO());
        when(r2Service.getPrivateObject(any(), anyInt())).thenReturn("https://signed.example/" + OBJECT_KEY);
    }

    private Ad adWith(AdStatus status) {
        User user = new User();
        user.setId(77L);

        CommercialDetails commercial = new CommercialDetails();
        commercial.setUser(user);

        AdAsset asset = new AdAsset();
        asset.setObjectKey(OBJECT_KEY);

        return Ad.builder()
                .id(AD_ID)
                .title("Anuncio de prueba")
                .status(status)
                .commercial(commercial)
                .asset(asset)
                .build();
    }

    @Nested
    @DisplayName("blockAdAsAdmin")
    class Block {

        @Test
        @DisplayName("revierte el objeto a privado en R2 y deja el anuncio BLOCKED")
        void block_revertsAssetToPrivate() {
            Ad ad = adWith(AdStatus.ACTIVE);
            when(adRepository.findById(AD_ID)).thenReturn(Optional.of(ad));

            adServiceImpl.blockAdAsAdmin(AD_ID);

            verify(r2Service).makeObjectPrivate(OBJECT_KEY);
            verify(r2Service, never()).makeObjectPublic(any());
            assertThat(ad.getStatus()).isEqualTo(AdStatus.BLOCKED);
        }

        @Test
        @DisplayName("no permite bloquear un anuncio ya rechazado y no toca R2")
        void block_rejectsInvalidState() {
            Ad ad = adWith(AdStatus.REJECTED);
            when(adRepository.findById(AD_ID)).thenReturn(Optional.of(ad));

            assertThatThrownBy(() -> adServiceImpl.blockAdAsAdmin(AD_ID))
                    .isInstanceOf(InvalidAdStateException.class);

            verify(r2Service, never()).makeObjectPrivate(any());
        }
    }

    @Nested
    @DisplayName("salida de BLOCKED")
    class Unblock {

        @Test
        @DisplayName("activateAdAsAdmin desde BLOCKED vuelve a publicar el objeto")
        void activateFromBlocked_republishesAsset() {
            Ad ad = adWith(AdStatus.BLOCKED);
            when(adRepository.findById(AD_ID)).thenReturn(Optional.of(ad));

            adServiceImpl.activateAdAsAdmin(AD_ID);

            verify(r2Service).makeObjectPublic(OBJECT_KEY);
            verify(r2Service, never()).makeObjectPrivate(any());
            assertThat(ad.getStatus()).isEqualTo(AdStatus.ACTIVE);
        }

        @Test
        @DisplayName("pauseAdAsAdmin desde BLOCKED vuelve a publicar el objeto")
        void pauseFromBlocked_republishesAsset() {
            Ad ad = adWith(AdStatus.BLOCKED);
            when(adRepository.findById(AD_ID)).thenReturn(Optional.of(ad));

            adServiceImpl.pauseAdAsAdmin(AD_ID);

            verify(r2Service).makeObjectPublic(OBJECT_KEY);
            assertThat(ad.getStatus()).isEqualTo(AdStatus.PAUSED);
        }

        @Test
        @DisplayName("activateAdAsAdmin desde APPROVED no toca el storage")
        void activateFromApproved_doesNotTouchStorage() {
            Ad ad = adWith(AdStatus.APPROVED);
            when(adRepository.findById(AD_ID)).thenReturn(Optional.of(ad));

            adServiceImpl.activateAdAsAdmin(AD_ID);

            verify(r2Service, never()).makeObjectPublic(any());
            verify(r2Service, never()).makeObjectPrivate(any());
        }

        @Test
        @DisplayName("pauseAdAsAdmin desde ACTIVE no toca el storage")
        void pauseFromActive_doesNotTouchStorage() {
            Ad ad = adWith(AdStatus.ACTIVE);
            when(adRepository.findById(AD_ID)).thenReturn(Optional.of(ad));

            adServiceImpl.pauseAdAsAdmin(AD_ID);

            verify(r2Service, never()).makeObjectPublic(any());
            verify(r2Service, never()).makeObjectPrivate(any());
        }
    }

    @Nested
    @DisplayName("contentUrl de un anuncio BLOCKED")
    class BlockedContentUrl {

        @Test
        @DisplayName("se entrega por URL prefirmada, nunca la URL pública del CDN")
        void blocked_usesPresignedUrl() {
            Ad ad = adWith(AdStatus.ACTIVE);
            when(adRepository.findById(AD_ID)).thenReturn(Optional.of(ad));

            AdResponseDTO dto = adServiceImpl.blockAdAsAdmin(AD_ID);

            verify(r2Service).getPrivateObject(eq(OBJECT_KEY), anyInt());
            verify(r2Service, never()).buildPublicUrl(any());
            assertThat(dto.getContentUrl()).startsWith("https://signed.example/");
        }
    }
}
