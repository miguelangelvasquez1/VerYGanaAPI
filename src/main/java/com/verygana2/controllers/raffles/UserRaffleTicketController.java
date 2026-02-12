package com.verygana2.controllers.raffles;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO;
import com.verygana2.dtos.raffle.responses.TicketBalanceResponseDTO;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleTicketStatus;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.services.interfaces.raffles.RaffleTicketService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/my/raffle-tickets")
@PreAuthorize("hasRole('ROLE_CONSUMER')")
@RequiredArgsConstructor
public class UserRaffleTicketController {

    private final RaffleTicketService raffleTicketService;

    @GetMapping
    public ResponseEntity<PagedResponse<RaffleTicketResponseDTO>> getUserTickets(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "status", required = false) RaffleTicketStatus status,
            @RequestParam(value = "source", required = false) RaffleTicketSource source,
            @RequestParam(value = "issuedAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime issuedAt,
            @PageableDefault(size = 10, page = 0) Pageable pageable) {

        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity
                .ok(raffleTicketService.getUserTickets(consumerId, status, source, issuedAt, pageable));
    }

    @GetMapping("/balance")
    public ResponseEntity<Long> getUserTotalTickets(@AuthenticationPrincipal Jwt jwt,
            @RequestParam("status") RaffleTicketStatus status) {
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(raffleTicketService.getUserTotalTickets(consumerId, status));
    }

    @GetMapping("/balance/by-raffle")
    public ResponseEntity<List<TicketBalanceResponseDTO>> getUserTicketBalanceByRaffle(
            @AuthenticationPrincipal Jwt jwt) {
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(raffleTicketService.getUserTicketBalanceByRaffle(consumerId));
    }

    @GetMapping("/balance/raffle/{raffleId}")
    public ResponseEntity<Long> getUserTicketBalanceInRaffle(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long raffleId, 
            @RequestParam("status") RaffleTicketStatus status) {
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(raffleTicketService.getUserTicketBalanceInRaffle(consumerId, raffleId, status));
    }

    @GetMapping("/eligibility/{raffleType}")
    public ResponseEntity<Boolean> canUserReceiveTickets(@AuthenticationPrincipal Jwt jwt,
            @PathVariable RaffleType raffleType) {
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(raffleTicketService.canUserReceiveTickets(consumerId, raffleType));
    }

}
