package com.verygana2.controllers.raffles;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.raffle.responses.ParticipantLeaderboardDTO;
import com.verygana2.dtos.raffle.responses.RaffleResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleStatsResponseDTO;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.services.interfaces.raffles.RaffleService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/raffles")
@RequiredArgsConstructor
public class RaffleController {

    private final RaffleService raffleService;

    @GetMapping
    public ResponseEntity<PagedResponse<RaffleResponseDTO>> getRafflesByStatusAndType (
            @RequestParam(value = "status", required = false) RaffleStatus status, @RequestParam(value = "type", required = false) RaffleType type,
            @PageableDefault(size = 10, page = 0, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(raffleService.getRafflesByStatusAndType(status, type, pageable));
    }

    @GetMapping("/{raffleId}")
    public ResponseEntity<RaffleResponseDTO> getRaffleById (@PathVariable Long raffleId){
        return ResponseEntity.ok(raffleService.getRaffleResponseDTOById(raffleId));
    }

    @GetMapping("/{raffleId}/stats")
    public ResponseEntity<RaffleStatsResponseDTO> getRaffleStats (@PathVariable Long raffleId){
        return ResponseEntity.ok(raffleService.getRaffleStats(raffleId));
    }

    @GetMapping("/{raffleId}/leaderboard")
    public ResponseEntity<List<ParticipantLeaderboardDTO>> getRaffleLeaderboard (@PathVariable Long raffleId){
        return ResponseEntity.ok(raffleService.getRaffleLeaderBoard(raffleId));
    }

    


}
