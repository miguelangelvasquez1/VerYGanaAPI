package com.verygana2.controllers.games;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.verygana2.dtos.game.EndSessionDTO;
import com.verygana2.dtos.game.EndSessionResponseDTO;
import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.exceptions.GlobalExceptionHandler;
import com.verygana2.services.interfaces.GameService;

/**
 * {@code POST /games/end-session} lo llama el juego desde el iframe, sin JWT: la sesión se
 * identifica solo con session_token + user_hash. Que la ruta sea pública lo prueba
 * PublicPathsAuthorizationTest; aquí, que el controller no dependa de un principal.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GameController: cierre de sesión desde el juego")
class GameControllerEndSessionTest {

    @Mock private GameService gameService;

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GameController(gameService, objectMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private ResultActions postEndSession(String sessionToken, String userHash) throws Exception {
        GameEventDTO<EndSessionDTO> event = new GameEventDTO<>();
        event.setSessionToken(sessionToken);
        event.setUserHash(userHash);
        event.setPayload(new EndSessionDTO(null, 1500, null));
        return mockMvc.perform(post("/games/end-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(event)));
    }

    @Test
    @DisplayName("sin JWT cierra la sesión y devuelve las llaves ganadas")
    void closesWithoutJwtAndReturnsKeys() throws Exception {
        when(gameService.completeSession(any())).thenReturn(new EndSessionResponseDTO(true, 13L));

        postEndSession("token-1", "hash-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rewardGranted").value(true))
                .andExpect(jsonPath("$.keysEarned").value(13));
    }

    @Test
    @DisplayName("sin session_token responde 400 y no toca el service")
    void missingSessionTokenIsRejected() throws Exception {
        postEndSession(null, "hash-1")
                .andExpect(status().isBadRequest());

        verifyNoInteractions(gameService);
    }

    @Test
    @DisplayName("sin user_hash responde 400 y no toca el service")
    void missingUserHashIsRejected() throws Exception {
        postEndSession("token-1", null)
                .andExpect(status().isBadRequest());

        verifyNoInteractions(gameService);
    }
}
