package com.verygana2.exceptions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Un cliente SSE que cierra la pestaña no es un error del servidor.
 *
 * <p>El envío siguiente (notificación o heartbeat) falla con IOException y Spring
 * re-despacha la request con ese error. En el catch-all eso era un "Unexpected error"
 * en ERROR más un HttpMessageNotWritableException, porque no hay converter que escriba
 * un ErrorResponse sobre una respuesta {@code text/event-stream}.
 */
@DisplayName("GlobalExceptionHandler — cliente SSE desconectado")
class GlobalExceptionHandlerClientDisconnectTest {

    @RestController
    static class FailingController {
        /** Reproduce el re-despacho: la respuesta ya es un stream cuando llega el error. */
        @GetMapping("/stream")
        void stream(HttpServletResponse response) throws IOException {
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            throw new IOException("An established connection was aborted by the software in your host machine");
        }

        @GetMapping("/plain")
        void plain() throws IOException {
            throw new IOException("disk full");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                // Sin esto gana el converter de Jackson-XML y el cuerpo del error se pierde.
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json().build()))
                .build();
    }

    @Test
    @DisplayName("sobre un stream no intenta escribir un cuerpo de error")
    void disconnectedStreamClientWritesNoErrorBody() throws Exception {
        mockMvc.perform(get("/stream"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("una IOException fuera de un stream sigue siendo un 500")
    void ioExceptionOutsideAStreamIsStillAServerError() throws Exception {
        mockMvc.perform(get("/plain"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unexpected error"));
    }
}
