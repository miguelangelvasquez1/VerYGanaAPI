package com.verygana2.controllers.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.controllers.GameController;
import com.verygana2.dtos.game.GameEventDTO;
import com.verygana2.dtos.game.GameMetricDTO;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.GlobalExceptionHandler;
import com.verygana2.exceptions.UnauthorizedException;
import com.verygana2.services.interfaces.GameService;
import com.verygana2.utils.audit.AuditConfig;

import jakarta.persistence.EntityNotFoundException;

/**
 * {@code POST /games/metrics} lo llama el juego desde el iframe, sin JWT. Es el cierre de la
 * partida (spec 013): el controller valida la credencial y delega en el service.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("GameController: métricas y cierre de la partida")
class GameControllerMetricsTest {

    @Mock private GameService gameService;

    // El mismo mapper que usa MVC en runtime
    private final ObjectMapper objectMapper = new AuditConfig().auditObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GameController(gameService, objectMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private ResultActions postBody(String body) throws Exception {
        return mockMvc.perform(post("/games/metrics")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions postMetrics(String sessionToken, String userHash, Boolean branded) throws Exception {
        GameEventDTO<List<GameMetricDTO>> event = new GameEventDTO<>();
        event.setSessionToken(sessionToken);
        event.setUserHash(userHash);
        event.setIsBrandedMode(branded);
        event.setPayload(List.of());
        return postBody(objectMapper.writeValueAsString(event));
    }

    @Test
    @DisplayName("delega en el service el cierre de la partida")
    void delegatesToServiceToCloseSession() throws Exception {
        postMetrics("token-1", "hash-1", true).andExpect(status().isOk());

        ArgumentCaptor<GameEventDTO<List<GameMetricDTO>>> captor = ArgumentCaptor.forClass(GameEventDTO.class);
        verify(gameService).submitGameMetrics(captor.capture());
        assertThat(captor.getValue().getSessionToken()).isEqualTo("token-1");
        assertThat(captor.getValue().getUserHash()).isEqualTo("hash-1");
    }

    @Test
    @DisplayName("sesión ya cerrada: el service vuelve sin excepción y responde 200")
    void alreadyClosedSessionReturns200() throws Exception {
        // submitGameMetrics es void: sin excepción equivale al no-op de una sesión cerrada
        postMetrics("token-1", "hash-1", true).andExpect(status().isOk());
        verify(gameService).submitGameMetrics(any());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"unauthorized,401", "notFound,404", "business,422"})
    @DisplayName("los errores de sesión conservan sus códigos")
    void sessionErrorsKeepCurrentStatusCodes(String kind, int expected) throws Exception {
        RuntimeException ex = switch (kind) {
            case "unauthorized" -> new UnauthorizedException("Session does not belong to user");
            case "notFound" -> new EntityNotFoundException("Session not found");
            default -> new BusinessException("Session expired");
        };
        doThrow(ex).when(gameService).submitGameMetrics(any());

        postMetrics("token-1", "hash-1", true).andExpect(status().is(expected));
    }

    @Test
    @DisplayName("session_token = preview responde 200 sin tocar el service")
    void previewReturns200WithoutCallingService() throws Exception {
        postMetrics("preview", "hash-1", true).andExpect(status().isOk());

        verifyNoInteractions(gameService);
    }

    @Test
    @DisplayName("sin session_token responde 400 y no toca el service")
    void missingSessionTokenIsRejected() throws Exception {
        postMetrics(null, "hash-1", true).andExpect(status().isBadRequest());

        verifyNoInteractions(gameService);
    }

    @Test
    @DisplayName("sin user_hash responde 400 y no toca el service")
    void missingUserHashIsRejected() throws Exception {
        postMetrics("token-1", null, true).andExpect(status().isBadRequest());

        verifyNoInteractions(gameService);
    }

    @Test
    @DisplayName("is_branded_mode nulo no da 500: decide la sesión")
    void nullBrandedModeIsNot500() throws Exception {
        postMetrics("token-1", "hash-1", null).andExpect(status().isOk());

        verify(gameService).submitGameMetrics(any());
    }

    @Test
    @DisplayName("is_branded_mode = false responde 200 sin tocar el service")
    void notBrandedModeReturns200WithoutCallingService() throws Exception {
        postMetrics("token-1", "hash-1", false).andExpect(status().isOk());

        verifyNoInteractions(gameService);
    }

    @Test
    @DisplayName("el cuerpo capturado de stack-tower se lee y se delega")
    void stackTowerCaptureReturns200AndDelegates() throws Exception {
        postBody(stackTowerBody()).andExpect(status().isOk());

        ArgumentCaptor<GameEventDTO<List<GameMetricDTO>>> captor = ArgumentCaptor.forClass(GameEventDTO.class);
        verify(gameService).submitGameMetrics(captor.capture());
        assertThat(captor.getValue().getCampaignId()).isEqualTo(14L);
        assertThat(captor.getValue().getPayload()).hasSize(7);
    }

    @Test
    @DisplayName("la salida no contiene token, hash ni cuerpo, ni en éxito ni en error")
    void outputNeverContainsTokenHashOrBody(CapturedOutput output) throws Exception {
        postBody(stackTowerBody()).andExpect(status().isOk());

        doThrow(new BusinessException("Session expired")).when(gameService).submitGameMetrics(any());
        postBody(stackTowerBody()).andExpect(status().isUnprocessableEntity());

        assertThat(output.getAll())
                .doesNotContain("token-stack-1")
                .doesNotContain("hash-stack-1")
                .doesNotContain("stack-tower");
    }

    private String stackTowerBody() throws Exception {
        return new String(new ClassPathResource("games/stack-tower-metrics.json").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }
}
