package com.verygana2.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
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
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.UnauthorizedException;
import com.verygana2.models.branding.Campaign;
import com.verygana2.models.games.GameSession;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.games.GameSessionRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.services.games.GameServiceImpl;
import com.verygana2.utils.games.GameConfigStamper;
import com.verygana2.utils.games.GameResponseEnvelope;

import jakarta.persistence.EntityNotFoundException;

/**
 * Quién puede leer la configuración de una campaña por {@code /games/assets}.
 *
 * El endpoint es público —el juego no lleva JWT—, y hasta ahora el service leía la
 * campaña por el {@code campaignId} del cuerpo sin mirar la sesión. Cualquiera podía
 * recorrer ids y leer campañas en DRAFT o pausadas, con su contenido de marca y sus
 * productos de recompensa. Estos tests fijan que la sesión es la credencial.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("/games/assets exige una sesión válida")
class GameAssetsSessionTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private GameSessionRepository gameSessionRepository;
    @Mock private ProductRepository productRepository;

    @Spy private GameConfigStamper gameConfigStamper = new GameConfigStamper("https://cdn/moneda.png");
    @Spy private GameResponseEnvelope gameResponseEnvelope = new GameResponseEnvelope();

    @InjectMocks private GameServiceImpl gameService;

    private static final String TOKEN = "3f1c2a9e-sesion";
    private static final String HASH = "hash-del-consumidor";

    private Campaign campaign21;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(gameService, "sessionExpirationTime", 30);

        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(32L);
        campaign21 = Campaign.builder()
            .id(21L)
            .commercial(commercial)
            .configData(Map.of("game", Map.of("words", List.of("Mangooo"))))
            .build();

        when(productRepository.findGameRewardsProducts(any())).thenReturn(List.of());
    }

    private void sessionExists(GameSession session) {
        when(gameSessionRepository.findBySessionToken(TOKEN)).thenReturn(Optional.of(session));
    }

    private static GameSession session(Campaign campaign) {
        return GameSession.builder().sessionToken(TOKEN).userHash(HASH).campaign(campaign).build();
    }

    private static GameEventDTO<Void> request(String token, String hash, Long campaignId) {
        GameEventDTO<Void> req = new GameEventDTO<>();
        req.setSessionToken(token);
        req.setUserHash(hash);
        req.setCampaignId(campaignId);
        return req;
    }

    @Test
    @DisplayName("con su sesión, el consumidor recibe la config de su campaña")
    @SuppressWarnings("unchecked")
    void validSessionGetsItsCampaign() {
        sessionExists(session(campaign21));

        Map<String, Object> assets = gameService.getGameAssets(request(TOKEN, HASH, 21L));

        assertThat((Map<String, Object>) assets.get("meta")).containsEntry("campaign_id", "21");
        assertThat(assets).containsKey("game");
    }

    @Test
    @DisplayName("sin sesión no se lee ninguna campaña: el caso de recorrer ids")
    void noSessionIsRejectedBeforeTouchingCampaigns() {
        // Es la petición exacta que antes devolvía la config de cualquier campaña.
        assertThatThrownBy(() -> gameService.getGameAssets(request(null, null, 21L)))
            .isInstanceOf(UnauthorizedException.class);

        verify(campaignRepository, never()).findById(anyLong());
        verify(gameSessionRepository, never()).findBySessionToken(any());
    }

    @Test
    @DisplayName("un token que no existe se rechaza")
    void unknownTokenIsRejected() {
        when(gameSessionRepository.findBySessionToken("inventado")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.getGameAssets(request("inventado", HASH, 21L)))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("el token de otro consumidor no sirve con un user_hash distinto")
    void foreignUserHashIsRejected() {
        sessionExists(session(campaign21));

        assertThatThrownBy(() -> gameService.getGameAssets(request(TOKEN, "otro-hash", 21L)))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("una sesión válida no abre otra campaña cambiando el campaign_id")
    void sessionCannotReadAnotherCampaign() {
        // Sin esto, cualquier consumidor con una sesión cualquiera volvía a poder
        // recorrer ids: la sesión validaría y la campaña saldría del cuerpo.
        sessionExists(session(campaign21));

        assertThatThrownBy(() -> gameService.getGameAssets(request(TOKEN, HASH, 22L)))
            .isInstanceOf(UnauthorizedException.class);

        verify(campaignRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("sin campaign_id en el cuerpo, la campaña sale de la sesión")
    @SuppressWarnings("unchecked")
    void campaignComesFromTheSession() {
        sessionExists(session(campaign21));

        Map<String, Object> assets = gameService.getGameAssets(request(TOKEN, HASH, null));

        assertThat((Map<String, Object>) assets.get("meta")).containsEntry("campaign_id", "21");
    }

    @Test
    @DisplayName("una sesión vencida ya no entrega la config")
    void expiredSessionIsRejected() {
        GameSession expired = session(campaign21);
        expired.setStartTime(ZonedDateTime.now().minusMinutes(31));
        sessionExists(expired);

        assertThatThrownBy(() -> gameService.getGameAssets(request(TOKEN, HASH, 21L)))
            .isInstanceOf(BusinessException.class);
    }
}
