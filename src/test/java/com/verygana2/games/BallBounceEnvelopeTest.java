package com.verygana2.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.models.branding.BrandingRequest;
import com.verygana2.models.branding.Campaign;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameConfigDefinition;
import com.verygana2.models.games.GameSession;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.games.GameSessionRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.services.games.GameServiceImpl;
import com.verygana2.utils.games.GameConfigStamper;
import com.verygana2.utils.games.GameResponseEnvelope;
import com.verygana2.utils.games.PreviewRewardSamples;
import com.verygana2.utils.validators.games.GameConfigValidator;

/**
 * El bug reportado el 2026-09-22: en Ball Bounce el diseñador llenaba todos los
 * campos del brandeo y la preview salía con el juego por defecto.
 *
 * La respuesta del backend era correcta y completa —está entera en el log del
 * navegador— y el juego igual imprimía {@code [BallBounceInitializer] Los datos del
 * juego son nulos tras el parseo}. No había error de parseo porque el JSON era
 * válido: el build lo deserializa en {@code ApiResponseWrapper}, cuyo único campo es
 * {@code game_data}, y nosotros mandábamos todo en la raíz.
 *
 * Estos tests fijan la forma de la respuesta para que el arreglo no se pierda en el
 * próximo cambio del payload.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Ball Bounce: la respuesta va envuelta en game_data")
class BallBounceEnvelopeTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private GameSessionRepository gameSessionRepository;
    @Mock private BrandingRequestRepository brandingRequestRepository;
    @Mock private ProductRepository productRepository;
    @Mock private GameConfigValidator gameConfigValidator;

    @Spy private GameConfigStamper gameConfigStamper = new GameConfigStamper("https://cdn/moneda.png");
    @Spy private GameResponseEnvelope gameResponseEnvelope = new GameResponseEnvelope();

    /** Real: sin productos del comercial, la preview usa ejemplos. */
    @Spy private PreviewRewardSamples previewRewardSamples = new PreviewRewardSamples("https://cdn/ejemplo.png");

    @InjectMocks private GameServiceImpl gameService;

    /** El @Value de la expiración no llega con @InjectMocks. */
    @BeforeEach
    void sessionExpiration() {
        ReflectionTestUtils.setField(gameService, "sessionExpirationTime", 30);
    }

    /** El slug sembrado para Ball Bounce: en cali es el game_title de la URL. */
    private static Game ballBounce() {
        return Game.builder().id(2L).title("Ball Bounce").url("Ball%20Bounce").build();
    }

    private static CommercialDetails commercial() {
        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(2L);
        return commercial;
    }

    /** Un brandeo completo, como el de la solicitud que falló. */
    private static Map<String, Object> config() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("game", Map.of("ball_speed", 500, "brick_rows", 5, "brick_columns", 9));
        config.put("texts", Map.of("victory_title", "¡VICTORIA!", "floating_words", List.of("Que bien")));
        config.put("branding", Map.of("images", Map.of("main_image_url", "https://cdn/logo.png")));
        config.put("audio", Map.of("music_url", "https://cdn/musica.mp3"));
        return config;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> gameData(Map<String, Object> response) {
        assertThat(response).containsKey("game_data");
        return (Map<String, Object>) response.get("game_data");
    }

    @Test
    @DisplayName("la preview entrega el brandeo dentro de game_data, no en la raíz")
    void previewIsWrapped() {
        BrandingRequest request = BrandingRequest.builder()
            .id(5L)
            .brandName("Coca Cola")
            .commercial(commercial())
            .game(ballBounce())
            .draftFormData(config())
            .build();

        when(brandingRequestRepository.findById(5L)).thenReturn(Optional.of(request));
        when(productRepository.findGameRewardsProducts(any())).thenReturn(List.of());
        when(gameConfigValidator.latestDefinition(any()))
            .thenReturn(GameConfigDefinition.builder().jsonSchema(Map.of()).build());

        Map<String, Object> response = gameService.getPreviewAssets(5L);

        // Esto es exactamente lo que fallaba: el brandeo estaba en la raíz y el juego
        // solo mira dentro de game_data.
        assertThat(gameData(response)).containsKeys("game", "texts", "branding", "audio", "meta");
        assertThat(response).doesNotContainKeys("game", "texts", "branding", "audio");
    }

    @Test
    @DisplayName("el reward_popup sigue en la raíz para el servicio común")
    void rewardPopupStaysAtRoot() {
        BrandingRequest request = BrandingRequest.builder()
            .id(5L)
            .brandName("Coca Cola")
            .commercial(commercial())
            .game(ballBounce())
            .draftFormData(config())
            .build();

        when(brandingRequestRepository.findById(5L)).thenReturn(Optional.of(request));
        when(productRepository.findGameRewardsProducts(any())).thenReturn(List.of());
        when(gameConfigValidator.latestDefinition(any()))
            .thenReturn(GameConfigDefinition.builder().jsonSchema(Map.of()).build());

        Map<String, Object> response = gameService.getPreviewAssets(5L);

        // SnowAssetsService lo lee de la raíz y no pasa por el wrapper del juego.
        assertThat(response).containsKey("reward_popup");
        assertThat(gameData(response)).containsKey("reward_popup");
    }

    @Test
    @DisplayName("la campaña real va envuelta igual que la preview")
    void campaignIsWrappedToo() {
        // Si solo se arreglara la preview, el diseño aprobado saldría a producción con
        // el mismo síntoma y ya sin nadie mirándolo.
        Campaign campaign = Campaign.builder()
            .id(42L)
            .commercial(commercial())
            .game(ballBounce())
            .configData(config())
            .build();

        when(gameSessionRepository.findBySessionToken("tok")).thenReturn(Optional.of(
                GameSession.builder().sessionToken("tok").userHash("u").campaign(campaign).build()));
        when(productRepository.findGameRewardsProducts(any())).thenReturn(List.of());

        GameEventDTO<Void> req = new GameEventDTO<>();
        req.setSessionToken("tok");
        req.setUserHash("u");
        req.setCampaignId(42L);

        Map<String, Object> response = gameService.getGameAssets(req);

        assertThat(gameData(response)).containsKeys("game", "texts", "branding", "meta");
        assertThat(response).containsOnlyKeys("game_data", "reward_popup");
    }
}
