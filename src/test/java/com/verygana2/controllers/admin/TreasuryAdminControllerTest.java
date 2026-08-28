package com.verygana2.controllers.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.treasury.TreasuryBalanceResponseDTO;
import com.verygana2.dtos.treasury.TreasuryMovementResponseDTO;
import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.services.interfaces.finance.TreasuryService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link TreasuryAdminController}. La clase está anotada con
 * {@code @PreAuthorize("hasRole('ADMIN')")}, salvo {@code getKeysReservePct()}
 * que tiene su propio override {@code @PreAuthorize("hasAnyRole('ADMIN', 'COMMERCIAL')")}
 * — la autorización real no se verifica en este test unitario (sin contexto de
 * Spring Security), queda cubierta por el test de integración correspondiente.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TreasuryAdminController")
class TreasuryAdminControllerTest {

    @Mock private TreasuryService treasuryService;

    private TreasuryConfig treasuryConfig;
    private TreasuryAdminController controller;

    @BeforeEach
    void setUp() {
        treasuryConfig = new TreasuryConfig();
        controller = new TreasuryAdminController(treasuryService, treasuryConfig);
    }

    @Nested
    @DisplayName("GET /balance")
    class GetBalance {

        @Test
        @DisplayName("delega en TreasuryService.getBalanceReport")
        void getBalance_delegates() {
            TreasuryBalanceResponseDTO expected = new TreasuryBalanceResponseDTO(
                    1_000_000L, 200_000L, 300_000L, 50_000L, 1_550_000L, 95.0, "OK", false);
            when(treasuryService.getBalanceReport()).thenReturn(expected);

            var response = controller.getBalance();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("GET /movements/{code}")
    class GetMovements {

        @Test
        @DisplayName("delega en TreasuryService.getMovements con el code del path y el Pageable, envuelto en PagedResponse")
        void getMovements_delegates() {
            TreasuryMovementResponseDTO movement = new TreasuryMovementResponseDTO(
                    UUID.randomUUID(), "KEYS_RESERVE", "PAYOUTS_PENDING", 10_000L,
                    "copago", UUID.randomUUID(), "Copayment", null);
            Pageable pageable = PageRequest.of(0, 20);
            Page<TreasuryMovementResponseDTO> page = new PageImpl<>(List.of(movement), pageable, 1);

            when(treasuryService.getMovements(TreasuryAccountCode.KEYS_RESERVE, pageable)).thenReturn(page);

            var response = controller.getMovements(TreasuryAccountCode.KEYS_RESERVE, pageable);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            PagedResponse<TreasuryMovementResponseDTO> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.getData()).containsExactly(movement);
            assertThat(body.getMeta().getTotalElements()).isEqualTo(1);
            verify(treasuryService).getMovements(TreasuryAccountCode.KEYS_RESERVE, pageable);
        }
    }

    @Nested
    @DisplayName("GET /config/keys-reserve-pct")
    class GetKeysReservePct {

        @Test
        @DisplayName("delega en TreasuryConfig.getKeysReservePct (endpoint con @PreAuthorize propio: ADMIN o COMMERCIAL)")
        void getKeysReservePct_delegates() {
            treasuryConfig.setKeysReservePct(60);

            var response = controller.getKeysReservePct();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(60);
        }
    }
}
