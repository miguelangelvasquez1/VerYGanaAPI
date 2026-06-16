package com.verygana2.controllers.raffles;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.raffle.requests.ClaimPrizeRequestDTO;
import com.verygana2.dtos.raffle.responses.PrizeWonResponseDTO;
import com.verygana2.dtos.raffle.responses.WinnerSummaryResponseDTO;
import com.verygana2.services.interfaces.TwilioSmsService;
import com.verygana2.services.interfaces.raffles.RaffleWinnerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/winners")
@RequiredArgsConstructor
public class RaffleWinnerController {

    private final RaffleWinnerService raffleWinnerService;
    private final TwilioSmsService twilioSmsService;

    @GetMapping("/raffle/{raffleId}")
    public ResponseEntity<List<WinnerSummaryResponseDTO>> getRaffleWinners(@PathVariable Long raffleId) {
        return ResponseEntity.ok(raffleWinnerService.getRaffleWinnersByRaffleId(raffleId));
    }

    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    @GetMapping("/my-prizes")
    public ResponseEntity<PagedResponse<PrizeWonResponseDTO>> getWonPrizes(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Boolean isClaimed,
            @PageableDefault(size = 10, page = 0, direction = Sort.Direction.DESC) Pageable pageable) {
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(raffleWinnerService.getWonPrizesList(consumerId, isClaimed, pageable));
    }

    @GetMapping("/last")
    public ResponseEntity<List<WinnerSummaryResponseDTO>> getLastWinners() {
        return ResponseEntity.ok(raffleWinnerService.getLastRaffleWinners());
    }

    /**
     * Paso 1 (solo si el ganador quiere entregar a un número de teléfono alternativo):
     * envía el OTP de Twilio Verify al número indicado.
     *
     * POST /api/winners/claim/send-otp?phoneNumber=3001234567
     */
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    @PostMapping("/claim/send-otp")
    public ResponseEntity<Void> sendClaimPhoneOtp(@RequestParam String phoneNumber) {
        twilioSmsService.sendOtp(phoneNumber);
        return ResponseEntity.accepted().build();
    }

    /**
     * Paso 2: reclamar el premio.
     * Si deliveryMethod=SMS y se proporcionó newPhoneNumber, incluir smsOtpCode.
     *
     * POST /api/winners/claim
     */
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    @PostMapping("/claim")
    public ResponseEntity<Void> claimPrize(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ClaimPrizeRequestDTO request) {
        Long consumerId = jwt.getClaim("userId");
        raffleWinnerService.claimPrize(consumerId, request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
