package com.verygana2.exceptions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.models.enums.finance.TreasuryAccountCode;

/**
 * Tesorería sin saldo es un 409 con mensaje de negocio, no un 500.
 *
 * <p>Antes todas las cuentas lanzaban {@code IllegalStateException}, que no tiene
 * handler: caía en el de {@code Exception} como "Unexpected error". Apareció en la
 * prueba de carga (hallazgo H3) con el gasto de llaves.
 */
@DisplayName("GlobalExceptionHandler — tesorería sin saldo")
class GlobalExceptionHandlerTreasuryTest {

    @RestController
    static class TreasuryController {
        @PostMapping("/keys-reserve")
        void keysReserve() {
            throw new KeysReserveInsufficientException();
        }

        @PostMapping("/payouts-pending")
        void payoutsPending() {
            throw new TreasuryInsufficientFundsException(TreasuryAccountCode.PAYOUTS_PENDING,
                    "[TREASURY] Saldo insuficiente en PAYOUTS_PENDING. disponible=123 requerido=456");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TreasuryController())
                .setControllerAdvice(new GlobalExceptionHandler())
                // Sin esto gana el converter de Jackson-XML y el cuerpo del error se pierde.
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json().build()))
                .build();
    }

    @Test
    @DisplayName("gasto de llaves sin reserva: 409 con mensaje para el consumidor")
    void keysReserveInsufficient_isConflict() throws Exception {
        mockMvc.perform(post("/keys-reserve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("gasto de llaves no está disponible")));
    }

    @Test
    @DisplayName("cuenta sin saldo: 409 que nombra la cuenta pero no filtra saldos")
    void treasuryInsufficient_isConflictWithoutAmounts() throws Exception {
        mockMvc.perform(post("/payouts-pending"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("PAYOUTS_PENDING")))
                .andExpect(jsonPath("$.message").value(not(containsString("123"))));
    }
}
