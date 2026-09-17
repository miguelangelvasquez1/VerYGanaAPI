package com.verygana2.controllers.games;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.controllers.GameController;
import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.GlobalExceptionHandler;
import com.verygana2.services.interfaces.GameService;

import jakarta.persistence.EntityNotFoundException;

/**
 * La preview del diseñador (session_token=preview) tiene que fallar diciendo qué
 * pasó.
 *
 * Antes el endpoint envolvía la llamada al service en un {@code try/catch} y
 * devolvía {@code 400} con cuerpo {@code null} para cualquier fallo: config sin
 * guardar, solicitud inexistente o error de verdad salían todos iguales. El juego
 * recibía una respuesta vacía y quien depuraba tenía que ir al log del servidor
 * para saber si el problema era suyo o nuestro.
 *
 * Estos tests van por MockMvc con el {@link GlobalExceptionHandler} real montado
 * como advice, porque el punto no es qué devuelve el método del controller sino
 * qué sale por HTTP: llamar al controller directo no distinguiría el arreglo del
 * bug. Cada caso mira el status y exige un mensaje en el cuerpo — con el
 * {@code try/catch} reintroducido, los tres devuelven 400 sin cuerpo y fallan.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GameController: preview de assets")
class GameControllerPreviewAssetsTest {

    @Mock private GameService gameService;

    // El mismo que arma Boot: un ObjectMapper pelado no sabe serializar el Instant
    // de ErrorResponse y el cuerpo del error se perdería por una razón que no es la
    // que estamos probando.
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GameController(gameService, objectMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                // Sin esto el standalone deja ganar el converter de Jackson-XML, que también
                // está en el classpath, y las respuestas salen en XML. Boot pone el de JSON
                // primero; acá hay que decirlo a mano para probar lo que devuelve la app.
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    /** El mismo ObjectMapper del controller serializa el body, para no fijar a mano el naming. */
    private String previewRequest(Long campaignId) throws Exception {
        GameEventDTO<Void> req = new GameEventDTO<>();
        req.setSessionToken("preview");
        req.setCampaignId(campaignId);
        return objectMapper.writeValueAsString(req);
    }

    private ResultActions postAssets(String body) throws Exception {
        return mockMvc.perform(post("/games/assets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("sin campaign_id responde 400 con el motivo, y no toca el service")
    void missingCampaignIdIsRejectedWithABody() throws Exception {
        postAssets(previewRequest(null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("campaign_id es requerido para la preview"));

        // El rechazo ocurre antes de la lectura: no se consulta ninguna solicitud.
        verifyNoInteractions(gameService);
    }

    @Test
    @DisplayName("un diseño sin configuración guardada responde 422 y dice cuál")
    void draftWithoutConfigSurfacesTheBusinessError() throws Exception {
        when(gameService.getPreviewAssets(5L)).thenThrow(new BusinessException(
                "El diseño de la solicitud 5 no tiene configuración guardada todavía"));

        postAssets(previewRequest(5L))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "El diseño de la solicitud 5 no tiene configuración guardada todavía"));
    }

    @Test
    @DisplayName("una solicitud inexistente responde 404, no el mismo 400 de todo lo demás")
    void missingBrandingRequestIsA404() throws Exception {
        when(gameService.getPreviewAssets(99L))
                .thenThrow(new EntityNotFoundException("Preview not found for id: 99"));

        postAssets(previewRequest(99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Preview not found for id: 99"));
    }

    @Test
    @DisplayName("con configuración, entrega el JSON del service tal cual")
    void happyPathReturnsTheAssets() throws Exception {
        when(gameService.getPreviewAssets(5L)).thenReturn(Map.of(
                "meta", Map.of("brand_id", "coca-cola", "campaign_id", "preview-5"),
                "reward_popup", Map.of("title", "¡Ganaste!")));

        postAssets(previewRequest(5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.brand_id").value("coca-cola"))
                .andExpect(jsonPath("$.meta.campaign_id").value("preview-5"))
                .andExpect(jsonPath("$.reward_popup.title").value("¡Ganaste!"));
    }

    @Test
    @DisplayName("session_token normal no entra por la preview aunque el campaign_id exista")
    void nonPreviewSessionDoesNotHitThePreviewPath() throws Exception {
        GameEventDTO<Void> req = new GameEventDTO<>();
        req.setSessionToken("una-sesion-real");
        req.setCampaignId(1L);

        postAssets(objectMapper.writeValueAsString(req)).andExpect(status().isOk());

        // getPreviewAssets es solo para BrandingRequests; una sesión real nunca debe caer ahí.
        verify(gameService, never()).getPreviewAssets(anyLong());
    }
}
