package com.verygana2.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
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
 * El popup de recompensas en la vista previa.
 *
 * Un comercial sin productos marcados como recompensa —lo normal mientras la campaña
 * se diseña— dejaba {@code reward_popup.products} vacío, y el juego mostraba «No hay
 * anunciantes por el momento». En producción eso es correcto: no hay nada que
 * ofrecer. En la preview impide revisar cómo queda ese bloque, que es parte de lo que
 * el diseñador y el anunciante tienen que aprobar.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Productos del popup en la preview")
class PreviewRewardFallbackTest {

    @Mock private BrandingRequestRepository brandingRequestRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private GameSessionRepository gameSessionRepository;
    @Mock private ProductRepository productRepository;
    @Mock private GameConfigValidator gameConfigValidator;

    @Spy private GameConfigStamper gameConfigStamper = new GameConfigStamper("https://cdn/moneda.png");
    @Spy private GameResponseEnvelope gameResponseEnvelope = new GameResponseEnvelope();
    @Spy private PreviewRewardSamples previewRewardSamples = new PreviewRewardSamples("https://cdn/ejemplo.png");

    @InjectMocks private GameServiceImpl gameService;

    /** El @Value de la expiración no llega con @InjectMocks. */
    @BeforeEach
    void sessionExpiration() {
        ReflectionTestUtils.setField(gameService, "sessionExpirationTime", 30);
    }

    private static CommercialDetails commercial() {
        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(31L);
        commercial.setCompanyName("Test Trivia");
        return commercial;
    }

    private static Game trivia() {
        return Game.builder().id(19L).title("Trivia Quiz").url("trivia-quiz").build();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> productsOf(Map<String, Object> assets) {
        return (List<Object>) ((Map<String, Object>) assets.get("reward_popup")).get("products");
    }

    private Map<String, Object> preview() {
        BrandingRequest request = BrandingRequest.builder()
            .id(13L)
            .brandName("Test Trivia")
            .commercial(commercial())
            .game(trivia())
            .draftFormData(Map.of("game", Map.of("questions", List.of(Map.of("question", "¿Una?")))))
            .build();

        when(brandingRequestRepository.findById(13L)).thenReturn(Optional.of(request));
        when(gameConfigValidator.latestDefinition(any()))
            .thenReturn(GameConfigDefinition.builder().jsonSchema(Map.of()).build());

        return gameService.getPreviewAssets(13L);
    }

    @Test
    @DisplayName("sin productos del comercial, la preview muestra ejemplos")
    void fallsBackToSamples() {
        when(productRepository.findGameRewardsProducts(any())).thenReturn(List.of());

        assertThat(productsOf(preview())).hasSize(3);
    }

    @Test
    @DisplayName("los ejemplos se ven como ejemplos: nadie debe confundirlos con recompensas reales")
    void samplesAreLabelled() {
        // La preview la mira el anunciante. Un producto inventado que parezca real es
        // peor que un popup vacío.
        var samples = previewRewardSamples.products("Test Trivia");

        assertThat(samples).allSatisfy(p -> {
            assertThat(p.getName()).containsIgnoringCase("ejemplo");
            assertThat(p.getImage_message()).contains("vista previa");
        });
    }

    @Test
    @DisplayName("los ejemplos no comparten id con ningún producto real")
    void samplesUseIdsNoProductHas() {
        // Con 1, 2 y 3, un popup que enlace por id abría productos reales de otro
        // comercial desde la preview.
        assertThat(previewRewardSamples.products("Test Trivia"))
            .allSatisfy(p -> assertThat(p.getId()).isNegative());
    }

    @Test
    @DisplayName("si el comercial sí tiene productos, no se inventa nada")
    void realProductsWin() {
        // El relleno es una muleta para revisar el diseño, no algo que deba taparle al
        // anunciante lo que de verdad va a ofrecer.
        ProductCategory categoria = new ProductCategory();
        categoria.setName("Entretenimiento");

        Product real = new Product();
        real.setId(77L);
        real.setName("Membresía Netflix");
        real.setCommercial(commercial());
        real.setPriceCents(4790000L);
        real.setMaxKeysPct(50);
        real.setAverageRate(4.4);
        real.setProductCategory(categoria);
        real.setStockItems(new java.util.ArrayList<>());

        when(productRepository.findGameRewardsProducts(31L)).thenReturn(List.of(real));

        List<Object> products = productsOf(preview());

        assertThat(products).hasSize(1);
        assertThat(products.get(0).toString()).contains("Membresía Netflix");
    }

    @Test
    @DisplayName("la campaña real nunca muestra ejemplos: si no hay productos, el popup va vacío")
    void realCampaignNeverFakesProducts() {
        // Acá está jugando un consumidor de verdad. Ofrecerle un producto que no existe
        // sería mentirle, y el juego ya sabe decir que no hay anunciantes.
        Campaign campaign = Campaign.builder()
            .id(42L)
            .commercial(commercial())
            .game(trivia())
            .configData(Map.of("game", Map.of("questions", List.of())))
            .build();

        when(gameSessionRepository.findBySessionToken("tok")).thenReturn(Optional.of(
                GameSession.builder().sessionToken("tok").userHash("u").campaign(campaign).build()));
        when(productRepository.findGameRewardsProducts(any())).thenReturn(List.of());

        GameEventDTO<Void> req = new GameEventDTO<>();
        req.setSessionToken("tok");
        req.setUserHash("u");
        req.setCampaignId(42L);

        assertThat(productsOf(gameService.getGameAssets(req))).isEmpty();
    }
}
