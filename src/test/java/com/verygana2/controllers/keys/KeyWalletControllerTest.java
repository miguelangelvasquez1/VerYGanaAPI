package com.verygana2.controllers.keys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import com.verygana2.dtos.keys.KeyBalanceResponseDTO;
import com.verygana2.dtos.keys.SpendKeysRequestDTO;
import com.verygana2.dtos.keys.SpendKeysResponseDTO;
import com.verygana2.services.interfaces.finance.KeyWalletService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link KeyWalletController}: cada endpoint resuelve el consumerId
 * desde el userId del JWT y delega en {@link KeyWalletService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KeyWalletController")
class KeyWalletControllerTest {

    @Mock private KeyWalletService keyWalletService;

    private KeyWalletController controller;

    @BeforeEach
    void setUp() {
        controller = new KeyWalletController(keyWalletService);
    }

    private Jwt jwtWithUserId(Long userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(userId);
        return jwt;
    }

    @Nested
    @DisplayName("GET /balance")
    class GetBalance {

        @Test
        @DisplayName("resuelve el consumer del JWT y delega en el service")
        void getBalance_delegates() {
            KeyBalanceResponseDTO expected = new KeyBalanceResponseDTO(50L, "KEYS");
            when(keyWalletService.getBalance(9L)).thenReturn(expected);

            ResponseEntity<KeyBalanceResponseDTO> response = controller.getBalance(jwtWithUserId(9L));

            assertThat(response.getBody()).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("POST /spend")
    class Spend {

        @Test
        @DisplayName("resuelve el consumer del JWT y delega en el service con el request")
        void spend_delegates() {
            SpendKeysRequestDTO request = new SpendKeysRequestDTO(1000L, 14, "14");
            SpendKeysResponseDTO expected = SpendKeysResponseDTO.ok(40L);

            when(keyWalletService.spendKeysForPetGame(9L, request)).thenReturn(expected);

            ResponseEntity<SpendKeysResponseDTO> response = controller.spend(jwtWithUserId(9L), request);

            assertThat(response.getBody()).isSameAs(expected);
        }
    }
}
