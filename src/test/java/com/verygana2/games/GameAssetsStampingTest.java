package com.verygana2.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.models.branding.BrandingRequest;
import com.verygana2.models.branding.Campaign;
import com.verygana2.models.games.GameConfigDefinition;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.services.games.GameServiceImpl;
import com.verygana2.utils.games.GameConfigStamper;
import com.verygana2.utils.validators.games.GameConfigValidator;

/**
 * El sellado de {@code meta} y {@code personalization} visto desde el service, con
 * el {@link GameConfigStamper} de verdad y no un mock.
 *
 * {@code GameConfigStamperTest} ya cubre la lógica del sellado; lo que falta probar
 * acá es el cableado, que es donde estaba el riesgo: cada camino le pasa argumentos
 * distintos y equivocarse no rompe nada visible. La campaña real manda
 * {@code brandId = null} a propósito —el valor bueno se selló en la entrega y una
 * lectura no debe pisarlo—, mientras que la preview sí lo calcula porque todavía no
 * hay Campaign. Invertirlo dejaría el {@code brand_id} de todas las campañas en
 * blanco sin que ningún test lo notara.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Sellado de la config entregada al juego")
class GameAssetsStampingTest {

    private static final String COIN = "https://cdn.verygana.com/public/logos/moneda-llave.png";

    @Mock private CampaignRepository campaignRepository;
    @Mock private BrandingRequestRepository brandingRequestRepository;
    @Mock private ProductRepository productRepository;
    @Mock private GameConfigValidator gameConfigValidator;

    /** El stamper real: el punto del test es que el service lo llame bien. */
    @Spy private GameConfigStamper gameConfigStamper = new GameConfigStamper(COIN);

    @InjectMocks private GameServiceImpl gameService;

    /** Como los esquemas de cali: declaran personalization con los dos iconos. */
    private static Map<String, Object> caliSchema() {
        return Map.of("properties", Map.of(
            "personalization", Map.of("properties", Map.of(
                "coin_url", Map.of("type", "string"),
                "coin_count_url", Map.of("type", "string")))));
    }

    private static CommercialDetails commercial() {
        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(2L);
        return commercial;
    }

    /** El meta tal como queda tras la entrega: brand_id sellado, campaign_id en blanco. */
    private static Map<String, Object> deliveredConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("meta", Map.of("brand_id", "coca-cola", "campaign_id", ""));
        config.put("game", Map.of("ball_speed", 500));
        return config;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(Map<String, Object> assets, String name) {
        return (Map<String, Object>) assets.get(name);
    }

    @Nested
    @DisplayName("campaña real")
    class RealCampaign {

        private Map<String, Object> assetsFor(Campaign campaign) {
            when(campaignRepository.findById(42L)).thenReturn(Optional.of(campaign));
            when(productRepository.findGameRewardsProducts(any())).thenReturn(List.of());

            GameEventDTO<Void> req = new GameEventDTO<>();
            req.setCampaignId(42L);
            return gameService.getGameAssets(req);
        }

        private Campaign campaign(GameConfigDefinition definition) {
            return Campaign.builder()
                .id(42L)
                .commercial(commercial())
                .configData(deliveredConfig())
                .configDefinition(definition)
                .build();
        }

        @Test
        @DisplayName("escribe el campaign_id real, que solo se conoce al aprobar")
        void stampsTheRealCampaignId() {
            Map<String, Object> assets = assetsFor(campaign(
                GameConfigDefinition.builder().jsonSchema(caliSchema()).build()));

            assertThat(block(assets, "meta")).containsEntry("campaign_id", "42");
        }

        @Test
        @DisplayName("no pisa el brand_id que quedó sellado en la entrega")
        void keepsTheBrandIdFromDelivery() {
            Map<String, Object> assets = assetsFor(campaign(
                GameConfigDefinition.builder().jsonSchema(caliSchema()).build()));

            // Si el service pasara un brandId calculado acá, todas las campañas saldrían
            // con el brand_id equivocado (o vacío: en la campaña no está el brandName).
            assertThat(block(assets, "meta")).containsEntry("brand_id", "coca-cola");
        }

        @Test
        @DisplayName("los juegos de cali reciben el icono de moneda del backend")
        void stampsCoinIcons() {
            Map<String, Object> assets = assetsFor(campaign(
                GameConfigDefinition.builder().jsonSchema(caliSchema()).build()));

            assertThat(block(assets, "personalization"))
                .containsEntry("coin_url", COIN)
                .containsEntry("coin_count_url", COIN);
        }

        @Test
        @DisplayName("una campaña sin configDefinition no revienta ni inventa personalization")
        void survivesMissingDefinition() {
            // configDefinition es opcional en el modelo: campañas viejas lo tienen nulo y
            // el sellado no puede tumbar la lectura de assets.
            Map<String, Object> assets = assetsFor(campaign(null));

            assertThat(block(assets, "meta")).containsEntry("campaign_id", "42");
            assertThat(assets).doesNotContainKey("personalization");
        }

        @Test
        @DisplayName("conserva el resto de la config y el reward_popup")
        void keepsTheRest() {
            Map<String, Object> assets = assetsFor(campaign(
                GameConfigDefinition.builder().jsonSchema(caliSchema()).build()));

            assertThat(assets).containsKeys("game", "reward_popup");
        }
    }

    @Nested
    @DisplayName("preview del diseñador")
    class Preview {

        private Map<String, Object> assetsFor(Map<String, Object> draft) {
            BrandingRequest request = BrandingRequest.builder()
                .id(5L)
                .brandName("Coca Cola")
                .commercial(commercial())
                .draftFormData(draft)
                .build();

            when(brandingRequestRepository.findById(5L)).thenReturn(Optional.of(request));
            when(productRepository.findGameRewardsProducts(any())).thenReturn(List.of());
            when(gameConfigValidator.latestDefinition(any()))
                .thenReturn(GameConfigDefinition.builder().jsonSchema(caliSchema()).build());

            return gameService.getPreviewAssets(5L);
        }

        @Test
        @DisplayName("deriva el brand_id del nombre de marca")
        void stampsBrandIdFromBrandName() {
            Map<String, Object> assets = assetsFor(Map.of("game", Map.of("ball_speed", 500)));

            assertThat(block(assets, "meta")).containsEntry("brand_id", "coca-cola");
        }

        @Test
        @DisplayName("marca el campaign_id como preview: todavía no existe la Campaign")
        void stampsAPreviewCampaignId() {
            Map<String, Object> assets = assetsFor(Map.of("game", Map.of("ball_speed", 500)));

            assertThat(block(assets, "meta")).containsEntry("campaign_id", "preview-5");
        }

        @Test
        @DisplayName("pisa el meta que haya escrito el diseñador a mano")
        void overwritesDesignerMeta() {
            // Valores reales de una solicitud de dash-runner.
            Map<String, Object> assets = assetsFor(Map.of(
                "meta", Map.of("brand_id", "Brand-id", "campaign_id", "brand id")));

            assertThat(block(assets, "meta"))
                .containsEntry("brand_id", "coca-cola")
                .containsEntry("campaign_id", "preview-5");
        }

        @Test
        @DisplayName("sella el icono de moneda encima del placeholder del esquema")
        void overwritesCoinPlaceholder() {
            Map<String, Object> assets = assetsFor(Map.of("personalization", Map.of(
                "coin_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COIN",
                "coin_count_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COUNT")));

            assertThat(block(assets, "personalization"))
                .containsEntry("coin_url", COIN)
                .containsEntry("coin_count_url", COIN);
        }

        @Test
        @DisplayName("sella después de aplanar los assets del borrador, no antes")
        void stampsOverTheStrippedDraft() {
            // El borrador guarda los assets como {assetId, url}; el juego espera la url
            // pelada. Si el sellado corriera antes del aplanado, meta viajaría envuelto.
            Map<String, Object> assets = assetsFor(Map.of(
                "game", Map.of("background", Map.of("assetId", 7, "url", "https://cdn/bg.png"))));

            assertThat(block(assets, "game")).containsEntry("background", "https://cdn/bg.png");
            assertThat(block(assets, "meta")).containsEntry("brand_id", "coca-cola");
        }
    }
}
