package com.verygana2.controllers.raffles;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO;
import com.verygana2.services.interfaces.raffles.RaffleTicketService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/my/raffle-tickets")
@PreAuthorize("hasRole('ROLE_CONSUMER')")
@RequiredArgsConstructor
public class UserRaffleTicketController {

    private final RaffleTicketService raffleTicketService;

    // Filtro para que el usuario pueda buscar sus boletos (ACTIVES OR EXPIRED)
    @GetMapping("/raffle/{raffleId}")
    public ResponseEntity<PagedResponse<RaffleTicketResponseDTO>> getUserTicketsByRaffle(@AuthenticationPrincipal Jwt jwt,
        @PathVariable Long raffleId, @PageableDefault(size = 10, page = 0) Pageable pageable) {

        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(raffleTicketService.getUserTicketsByRaffle(consumerId, raffleId, pageable));
    }

    // Retorna los boletos ganadores del usuario
    @GetMapping("/winners")
    public ResponseEntity<PagedResponse<RaffleTicketResponseDTO>> getUserWinnerTickets (@AuthenticationPrincipal Jwt jwt, @PageableDefault(size = 10, page = 0) Pageable pageable){
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(raffleTicketService.getUserWinnerTickets(consumerId, pageable));
    }

    // Retorna la cantidad numerica de boletos ganadores del usuario
    @GetMapping("/winners/balance")  
    public ResponseEntity<Long> getWinnerUserTotalTickets (@AuthenticationPrincipal Jwt jwt){
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(raffleTicketService.getUserWinnerTotalTickets(consumerId));
    }

}
