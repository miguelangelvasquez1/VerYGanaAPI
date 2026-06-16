package com.verygana2.controllers.raffles;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.raffle.responses.DrawProofResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleResultResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleSummaryResultResponseDTO;
import com.verygana2.services.interfaces.raffles.RaffleResultService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/results")
@RequiredArgsConstructor
public class RaffleResultController {

    private final RaffleResultService raffleResultService;

    @GetMapping("/raffle/{raffleId}")
    public ResponseEntity<RaffleResultResponseDTO> getRaffleResultByRaffleId(@PathVariable("raffleId") Long raffleId) {
        return ResponseEntity.ok(raffleResultService.getResultByRaffleId(raffleId));
    }

    @GetMapping("/last")
    public ResponseEntity<List<RaffleSummaryResultResponseDTO>> getLastRaffleResults() {
        return ResponseEntity.ok(raffleResultService.getLastRaffleResults());
    }

    @GetMapping("/raffle/{raffleId}/draw-proof")
    public ResponseEntity<DrawProofResponseDTO> getDrawProofByRaffleId(
            @PathVariable Long raffleId) {

        return ResponseEntity.ok(raffleResultService.getDrawProofByRaffleId(raffleId));
    }
    
}
