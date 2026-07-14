package com.verygana2.controllers.raffles;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.raffle.requests.ClaimPrizeRequestDTO;
import com.verygana2.dtos.raffle.responses.PrizeWonResponseDTO;
import com.verygana2.dtos.raffle.responses.WinnerSummaryResponseDTO;
import com.verygana2.services.interfaces.EmailVerificationService;
import com.verygana2.services.interfaces.TwilioSmsService;
import com.verygana2.services.interfaces.raffles.RaffleWinnerService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests de {@link RaffleWinnerController}: ganadores públicos, mis premios, y el flujo de reclamo (OTP + claim). */
@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleWinnerController")
class RaffleWinnerControllerTest {

    @Mock private RaffleWinnerService raffleWinnerService;
    @Mock private TwilioSmsService twilioSmsService;
    @Mock private EmailVerificationService emailVerificationService;

    private RaffleWinnerController controller;

    @BeforeEach
    void setUp() {
        controller = new RaffleWinnerController(raffleWinnerService, twilioSmsService, emailVerificationService);
    }

    private Jwt jwtWithUserId(Long userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(userId);
        return jwt;
    }

    @Test
    @DisplayName("getRaffleWinners: delega en el service con el raffleId del path")
    void getRaffleWinners_delegates() {
        List<WinnerSummaryResponseDTO> expected = List.of(WinnerSummaryResponseDTO.builder().build());
        when(raffleWinnerService.getRaffleWinnersByRaffleId(1L)).thenReturn(expected);

        assertThat(controller.getRaffleWinners(1L).getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getWonPrizes: extrae el consumerId del JWT y pasa el filtro de status")
    void getWonPrizes_delegates() {
        var pageable = PageRequest.of(0, 10);
        var expected = PagedResponse.<PrizeWonResponseDTO>builder().build();
        when(raffleWinnerService.getWonPrizesList(9L, null, pageable)).thenReturn(expected);

        var response = controller.getWonPrizes(jwtWithUserId(9L), null, pageable);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("sendClaimPhoneOtp: delega en TwilioSmsService y responde 202 ACCEPTED")
    void sendClaimPhoneOtp_returns202() {
        var response = controller.sendClaimPhoneOtp("3001234567");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(twilioSmsService).sendOtp("3001234567");
    }

    @Test
    @DisplayName("sendClaimEmailOtp: delega en EmailVerificationService y responde 202 ACCEPTED")
    void sendClaimEmailOtp_returns202() {
        var response = controller.sendClaimEmailOtp("nuevo@correo.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(emailVerificationService).sendVerificationCode("nuevo@correo.com");
    }

    @Test
    @DisplayName("claimPrize: delega con el consumerId del JWT y responde 204 NO_CONTENT")
    void claimPrize_returns204() {
        ClaimPrizeRequestDTO request = new ClaimPrizeRequestDTO();

        var response = controller.claimPrize(jwtWithUserId(9L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(raffleWinnerService).claimPrize(9L, request);
    }
}
