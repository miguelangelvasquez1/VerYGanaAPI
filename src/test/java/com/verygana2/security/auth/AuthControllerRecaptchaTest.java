package com.verygana2.security.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.dtos.auth.AuthRequest;
import com.verygana2.dtos.user.CommercialRegisterDTO;
import com.verygana2.dtos.user.ConsumerRegisterDTO;
import com.verygana2.security.auth.refreshToken.SecurityAuditService;
import com.verygana2.security.recaptcha.RecaptchaService;
import com.verygana2.services.interfaces.PasswordSetupService;
import com.verygana2.services.interfaces.UserService;
import com.verygana2.services.interfaces.raffles.TicketDeliveryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de la puerta de reCAPTCHA de {@link AuthController}.
 *
 * Cubre el bug que estuvo 8 días en main: el controller verificaba los tres
 * endpoints contra la acción "login", así que todo registro era rechazado.
 * Como el rechazo ocurre ANTES de llamar a UserService, el efecto colateral
 * era que tampoco se creaba el usuario ni salía el correo de verificación.
 *
 * No cubre el flujo completo de login (tokens, cookies, tickets); eso va en
 * un test aparte.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController - puerta de reCAPTCHA")
class AuthControllerRecaptchaTest {

    private static final String TOKEN = "token-de-prueba";

    @Mock TokenService tokenService;
    @Mock AuthenticationManager authManager;
    @Mock UserService userService;
    @Mock TicketDeliveryService ticketDeliveryService;
    @Mock PasswordSetupService passwordSetupService;
    @Mock SecurityAuditService securityAuditService;
    @Mock AccountLockService accountLockService;
    @Mock RecaptchaService recaptchaService;

    @InjectMocks AuthController controller;

    @BeforeEach
    void setUp() {
        // Espejo de los defaults de application.yml
        ReflectionTestUtils.setField(controller, "loginRecaptchaAction", "login");
        ReflectionTestUtils.setField(controller, "registerConsumerRecaptchaAction", "register_consumer");
        ReflectionTestUtils.setField(controller, "registerCommercialRecaptchaAction", "register_commercial");
    }

    private static AuthRequest authRequest() {
        AuthRequest request = new AuthRequest();
        request.setIdentifier("usuario@test.com");
        request.setPassword("secreta");
        request.setRecaptchaToken(TOKEN);
        return request;
    }

    private static ConsumerRegisterDTO consumerDto() {
        ConsumerRegisterDTO dto = new ConsumerRegisterDTO();
        dto.setRecaptchaToken(TOKEN);
        return dto;
    }

    private static CommercialRegisterDTO commercialDto() {
        CommercialRegisterDTO dto = new CommercialRegisterDTO();
        dto.setRecaptchaToken(TOKEN);
        return dto;
    }

    @Nested
    @DisplayName("cada endpoint manda su propia acción")
    class ActionPerEndpoint {

        @Test
        @DisplayName("login, consumer y commercial usan tres acciones distintas")
        void eachEndpointSendsItsOwnAction() {
            // Regresión directa del bug: los tres endpoints mandaban "login".
            // recaptchaService.verify devuelve false por defecto, así que cada
            // llamada corta de inmediato y solo queda registrada la acción.
            assertThatThrownBy(() -> controller.login(authRequest(), "web", new MockHttpServletRequest()))
                    .isInstanceOf(BadCredentialsException.class);
            assertThatThrownBy(() -> controller.registerConsumer(consumerDto()))
                    .isInstanceOf(BadCredentialsException.class);
            assertThatThrownBy(() -> controller.registerCommercial(commercialDto()))
                    .isInstanceOf(BadCredentialsException.class);

            ArgumentCaptor<String> actions = ArgumentCaptor.forClass(String.class);
            verify(recaptchaService, times(3)).verify(eq(TOKEN), actions.capture());

            assertThat(actions.getAllValues())
                    .containsExactly("login", "register_consumer", "register_commercial")
                    .doesNotHaveDuplicates();
        }
    }

    @Nested
    @DisplayName("reCAPTCHA rechaza")
    class RecaptchaRejects {

        @Test
        @DisplayName("login: no intenta autenticar")
        void login_doesNotAuthenticate() {
            assertThatThrownBy(() -> controller.login(authRequest(), "web", new MockHttpServletRequest()))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("No fue posible verificar la seguridad de la solicitud.");

            verify(authManager, never()).authenticate(any());
            verify(tokenService, never()).generateTokenPair(any());
        }

        @Test
        @DisplayName("consumer: no crea el usuario")
        void registerConsumer_doesNotCreateUser() {
            assertThatThrownBy(() -> controller.registerConsumer(consumerDto()))
                    .isInstanceOf(BadCredentialsException.class);

            verify(userService, never()).registerConsumer(any());
        }

        @Test
        @DisplayName("commercial: no crea el usuario ni dispara el correo de verificación")
        void registerCommercial_doesNotCreateUser() {
            // El correo de verificación se manda dentro de registerCommercial.
            // Si nunca se llama, el usuario nunca recibe nada: ese fue el
            // segundo síntoma del bug.
            assertThatThrownBy(() -> controller.registerCommercial(commercialDto()))
                    .isInstanceOf(BadCredentialsException.class);

            verify(userService, never()).registerCommercial(any());
        }
    }

    @Nested
    @DisplayName("reCAPTCHA aprueba")
    class RecaptchaAccepts {

        @Test
        @DisplayName("consumer: registra y responde 201")
        void registerConsumer_createsUser() {
            when(recaptchaService.verify(anyString(), eq("register_consumer"))).thenReturn(true);

            ConsumerRegisterDTO dto = consumerDto();
            ResponseEntity<?> response = controller.registerConsumer(dto);

            verify(userService).registerConsumer(dto);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).asString().contains("Revisa tu correo");
        }

        @Test
        @DisplayName("consumer PEP: responde que queda en revisión de cumplimiento")
        void registerConsumerPep_returnsComplianceMessage() {
            when(recaptchaService.verify(anyString(), eq("register_consumer"))).thenReturn(true);

            ConsumerRegisterDTO dto = consumerDto();
            dto.setIsPEP(true);

            ResponseEntity<?> response = controller.registerConsumer(dto);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).asString().contains("revisión por el equipo de cumplimiento");
        }

        @Test
        @DisplayName("commercial: registra y responde 201")
        void registerCommercial_createsUser() {
            when(recaptchaService.verify(anyString(), eq("register_commercial"))).thenReturn(true);

            CommercialRegisterDTO dto = commercialDto();
            ResponseEntity<?> response = controller.registerCommercial(dto);

            verify(userService).registerCommercial(dto);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).asString().contains("Revisa tu correo");
        }
    }
}
