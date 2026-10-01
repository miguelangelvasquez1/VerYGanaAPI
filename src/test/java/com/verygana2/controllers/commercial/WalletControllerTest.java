package com.verygana2.controllers.commercial;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.wallet.responses.BillingSummaryResponseDTO;
import com.verygana2.dtos.wallet.responses.DepositResponseDTO;
import com.verygana2.dtos.wallet.responses.PayoutSummaryResponseDTO;
import com.verygana2.models.enums.finance.WalletStatus;
import com.verygana2.services.interfaces.finance.WalletService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link WalletController}: cada endpoint resuelve el commercialId
 * desde el userId del JWT y delega en {@link WalletService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WalletController")
class WalletControllerTest {

    @Mock private WalletService walletService;

    private WalletController controller;

    @BeforeEach
    void setUp() {
        controller = new WalletController(walletService);
    }

    private Jwt jwtWithUserId(Long userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(userId);
        return jwt;
    }

    @Nested
    @DisplayName("GET /me/billing-summary")
    class GetBillingSummary {

        @Test
        @DisplayName("resuelve el commercial del JWT y delega en el service")
        void getBillingSummary_delegates() {
            BillingSummaryResponseDTO expected = BillingSummaryResponseDTO.builder()
                    .balanceCents(100_000L)
                    .walletStatus(WalletStatus.ACTIVE)
                    .build();
            when(walletService.getBillingSummary(9L)).thenReturn(expected);

            ResponseEntity<BillingSummaryResponseDTO> response = controller.getBillingSummary(jwtWithUserId(9L));

            assertThat(response.getBody()).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("GET /me/deposits")
    class GetDeposits {

        @Test
        @DisplayName("resuelve el commercial del JWT y delega en el service con año, mes y pageable")
        void getDeposits_delegates() {
            Pageable pageable = PageRequest.of(0, 10);
            PagedResponse<DepositResponseDTO> expected =
                    PagedResponse.<DepositResponseDTO>builder().data(List.of()).build();
            when(walletService.getDeposits(9L, 2026, 8, pageable)).thenReturn(expected);

            ResponseEntity<PagedResponse<DepositResponseDTO>> response =
                    controller.getDeposits(jwtWithUserId(9L), 2026, 8, pageable);

            verify(walletService).getDeposits(eq(9L), eq(2026), eq(8), eq(pageable));
            assertThat(response.getBody()).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("GET /me/payouts")
    class GetPayouts {

        @Test
        @DisplayName("resuelve el commercial del JWT y delega en el service con año, mes y pageable")
        void getPayouts_delegates() {
            Pageable pageable = PageRequest.of(0, 10);
            PagedResponse<PayoutSummaryResponseDTO> expected =
                    PagedResponse.<PayoutSummaryResponseDTO>builder().data(List.of()).build();
            when(walletService.getPayouts(9L, 2026, 8, pageable)).thenReturn(expected);

            ResponseEntity<PagedResponse<PayoutSummaryResponseDTO>> response =
                    controller.getPayouts(jwtWithUserId(9L), 2026, 8, pageable);

            verify(walletService).getPayouts(eq(9L), eq(2026), eq(8), eq(pageable));
            assertThat(response.getBody()).isSameAs(expected);
        }
    }
}
