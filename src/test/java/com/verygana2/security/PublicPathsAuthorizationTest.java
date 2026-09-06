package com.verygana2.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.verygana2.config.SecurityConfig;
import com.verygana2.controllers.UserController;
import com.verygana2.security.systemFeatures.FeatureFlagService;
import com.verygana2.services.interfaces.UserService;
import com.verygana2.testsupport.TestRsaKeys;

/**
 * Matriz de autorización sobre la cadena de filtros REAL de {@link SecurityConfig}
 * y la lista {@link PublicPaths}. Cada caso hace la petición SIN token y afirma
 * lo que la seguridad DEBERÍA responder.
 *
 * <p>Un fallo aquí no es un test roto: es un endpoint expuesto a anónimos. El
 * riesgo concreto que persigue esta clase es que {@code /users/**} está en
 * {@link PublicPaths#PATHS}, lo que deja {@code UserController} entero en
 * {@code permitAll()} — incluido el borrado de usuarios.
 *
 * <p>Solo se registra {@link UserController}; el resto de rutas se evalúan a
 * nivel de cadena de filtros, que corre antes del DispatcherServlet.
 */
@WebMvcTest(controllers = UserController.class)
@Import(SecurityConfig.class)
@DisplayName("PublicPaths — matriz de autorización (seguridad)")
class PublicPathsAuthorizationTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    /** Lo exige el FeatureFlagInterceptor que @WebMvcTest registra por ser WebMvcConfigurer. */
    @MockitoBean
    private FeatureFlagService featureFlagService;

    /**
     * SecurityConfig necesita llaves RSA para construir el jwtDecoder. No basta un
     * bean @Primary: el perfil dev apunta rsa.private-key a un .pem que no está
     * versionado, y el bind de RsaKeyProperties falla antes de que la precedencia
     * importe. Hay que sobrescribir la propiedad, no el bean.
     */
    @DynamicPropertySource
    static void rsaKeys(DynamicPropertyRegistry registry) {
        TestRsaKeys.register(registry);
    }

    @Nested
    @DisplayName("UserController sin autenticación")
    class AnonymousAccessToUserEndpoints {

        @Test
        @DisplayName("DELETE /users/delete/id/{id} debe denegarse y NO llegar al service")
        void anonymousCannotDeleteUsers() {
            assertThat(mvc.delete().uri("/users/delete/id/1"))
                    .hasStatus4xxClientError();

            verify(userService, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("GET /users/id/{id} debe denegarse (expone PII de cualquier usuario)")
        void anonymousCannotReadUserById() {
            assertThat(mvc.get().uri("/users/id/1"))
                    .hasStatus4xxClientError();

            verify(userService, never()).getUserById(anyLong());
        }

        @Test
        @DisplayName("GET /users/email/{email} debe denegarse (expone PII por email)")
        void anonymousCannotReadUserByEmail() {
            assertThat(mvc.get().uri("/users/email/victima@test.com"))
                    .hasStatus4xxClientError();

            verify(userService, never()).getUserByEmail(anyString());
        }

        @Test
        @DisplayName("GET /users/me sin token se deniega (el @PreAuthorize sí protege)")
        void anonymousCannotReadMe() {
            assertThat(mvc.get().uri("/users/me"))
                    .hasStatus4xxClientError();
        }

        @Test
        @DisplayName("GET /users/exists/email/{email}: público a propósito, lo necesita el registro")
        void emailExistsStaysPublic() {
            assertThat(mvc.get().uri("/users/exists/email/alguien@test.com"))
                    .hasStatusOk();
        }
    }

    @Nested
    @DisplayName("comportamiento por defecto de la cadena")
    class DefaultDeny {

        @Test
        @DisplayName("una ruta que no está en PublicPaths se deniega sin token")
        void nonPublicPathIsDenied() {
            assertThat(mvc.get().uri("/wallets/me"))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("contenido de PublicPaths")
    class PublicPathsContent {

        @Test
        @DisplayName("no expone /users/** completo: obliga a listar solo lo realmente público")
        void doesNotWhitelistAllUserEndpoints() {
            assertThat(PublicPaths.PATHS).doesNotContain("/users/**");
        }

        @Test
        @DisplayName("ninguna ruta administrativa es pública")
        void noAdminPathIsPublic() {
            assertThat(PublicPaths.PATHS)
                    .noneMatch(path -> path.startsWith("/admin"));
        }
    }
}