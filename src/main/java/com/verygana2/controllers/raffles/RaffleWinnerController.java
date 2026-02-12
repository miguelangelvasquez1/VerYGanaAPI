package com.verygana2.controllers.raffles;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.verygana2.dtos.raffle.responses.PrizeWonResponseDTO;
import com.verygana2.dtos.raffle.responses.WinnerSummaryResponseDTO;
import com.verygana2.services.interfaces.raffles.RaffleWinnerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/winners")
@RequiredArgsConstructor
public class RaffleWinnerController {
    
    private final RaffleWinnerService raffleWinnerService;

    @GetMapping("/raffle/{raffleId}")
    public ResponseEntity<List<WinnerSummaryResponseDTO>> getRaffleWinners (@PathVariable Long raffleId){
        return ResponseEntity.ok(raffleWinnerService.getRaffleWinnersList(raffleId));
    }

    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    @GetMapping("/my-prizes")
    public ResponseEntity<List<PrizeWonResponseDTO>> getWonPrizes (@AuthenticationPrincipal Jwt jwt, @PageableDefault(size = 10, page = 0, sort = "drawnAt", direction = Sort.Direction.DESC) Pageable pageable){
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(raffleWinnerService.getWonPrizesList(consumerId, pageable));
    }

    //Aqui iria el metodo de reclamar premio pero aun no lo he hecho
}
