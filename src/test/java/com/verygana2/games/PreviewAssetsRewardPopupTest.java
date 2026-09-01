package com.verygana2.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.verygana2.models.branding.BrandingRequest;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.services.games.GameServiceImpl;

/**
 * La preview del diseñador tiene que entregar la misma estructura que el juego real.
 *
 * Caso real en ball-bounce: {@code getGameAssets} inyectaba {@code reward_popup} y
 * {@code getPreviewAssets} no. El build lo mapea a {@code GameCampaignData.products_data},
 * y sin ese bloque el parseo terminaba en null y el juego cargaba su configuración por
 * defecto — con el brandeo completo y correcto en la respuesta. El diseñador veía su
 * diseño ignorado sin ningún error que lo explicara.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreviewAssetsRewardPopupTest {

    @Mock private BrandingRequestRepository brandingRequestRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks private GameServiceImpl gameService;

    private static CommercialDetails commercial() {
        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(2L);
        return commercial;
    }

    private void givenRequestWithDraft(Map<String, Object> draft) {
        BrandingRequest request = BrandingRequest.builder()
            .id(5L)
            .commercial(commercial())
            .draftFormData(draft)
            .build();

        when(brandingRequestRepository.findById(5L)).thenReturn(Optional.of(request));
        when(productRepository.findGameRewardsProducts(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("la preview incluye reward_popup, igual que el juego real")
    @SuppressWarnings("unchecked")
    void previewIncludesRewardPopup() {
        givenRequestWithDraft(Map.of("game", Map.of("ball_speed", 500)));

        Map<String, Object> assets = gameService.getPreviewAssets(5L);

        assertThat(assets).containsKey("reward_popup");

        Map<String, Object> popup = (Map<String, Object>) assets.get("reward_popup");
        assertThat(popup).containsKeys("popup_title", "products");
    }

    @Test
    @DisplayName("la preview conserva la configuración del borrador")
    void previewKeepsDraftConfig() {
        givenRequestWithDraft(Map.of("game", Map.of("ball_speed", 500)));

        assertThat(gameService.getPreviewAssets(5L)).containsKey("game");
    }

    @Test
    @DisplayName("un borrador vacío no arma un popup a medias")
    void emptyDraftReturnsEmpty() {
        givenRequestWithDraft(Map.of());

        assertThat(gameService.getPreviewAssets(5L)).isEmpty();
    }
}
