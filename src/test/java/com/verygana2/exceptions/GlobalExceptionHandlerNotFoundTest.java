package com.verygana2.exceptions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Una ruta que no existe es un 404, no un 500.
 *
 * <p>Desde Spring 6.1 llega como {@link NoResourceFoundException}. Sin handler propio caía
 * en el de {@code Exception}: 500 con stacktrace en ERROR, y cada bot que prueba rutas
 * como /wp-login.php inflaba la tasa de 5xx que vigila la alerta TasaDeErrores5xx. Pasó
 * también con /swagger-ui.html al apagar Swagger en prod.
 */
@DisplayName("GlobalExceptionHandler — ruta inexistente")
class GlobalExceptionHandlerNotFoundTest {

    /** Hace lo que hace el DispatcherServlet cuando ningún handler ni recurso casa. */
    @RestController
    static class MissingResourceController {
        @GetMapping("/wp-login.php")
        void missing() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "wp-login.php");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MissingResourceController())
                .setControllerAdvice(new GlobalExceptionHandler())
                // Sin esto gana el converter de Jackson-XML y el cuerpo del error se pierde.
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json().build()))
                .build();
    }

    @Test
    @DisplayName("responde 404 con la ruta en el mensaje, no 500")
    void unknownPathIsNotFound() throws Exception {
        mockMvc.perform(get("/wp-login.php"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ruta no encontrada: [GET] /wp-login.php"));
    }
}