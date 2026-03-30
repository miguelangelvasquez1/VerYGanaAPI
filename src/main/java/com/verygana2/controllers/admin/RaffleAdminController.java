package com.verygana2.controllers.admin;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.raffle.requests.ConfirmRaffleCreationRequestDTO;
import com.verygana2.dtos.raffle.requests.PrepareRaffleCreationRequestBodyDTO;
import com.verygana2.dtos.raffle.requests.UpdateRaffleRequestDTO;
import com.verygana2.dtos.raffle.responses.DrawResultResponseDTO;
import com.verygana2.dtos.raffle.responses.PrepareRaffleCreationResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleResponseDTO;
import com.verygana2.models.enums.raffles.RaffleStatus;

import com.verygana2.services.interfaces.raffles.DrawingService;
import com.verygana2.services.interfaces.raffles.RaffleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/raffles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class RaffleAdminController {

    private final DrawingService drawingService;
    private final RaffleService raffleService;

    @PostMapping("/prepare")
    public ResponseEntity<PrepareRaffleCreationResponseDTO> prepareRaffleCreation(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid PrepareRaffleCreationRequestBodyDTO request) {
        
        Long adminId = jwt.getClaim("userId");
        PrepareRaffleCreationResponseDTO response = raffleService.prepareRaffleCreation(
                adminId,
                request.getRaffleData(),
                request.getRaffleImageMetadata(),
                request.getPrizeImageMetadataList());

        return ResponseEntity.ok(response);
    } 

    @PostMapping("/confirm")
    public ResponseEntity<EntityCreatedResponseDTO> confirmRaffleCreation(
            @AuthenticationPrincipal Jwt jwt, @RequestBody @Valid ConfirmRaffleCreationRequestDTO request) {
        Long adminId = jwt.getClaim("userId");
        EntityCreatedResponseDTO response = raffleService.confirmRaffleCreation(adminId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{raffleId}")
    public ResponseEntity<EntityUpdatedResponseDTO> updateRaffle(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long raffleId,
            @RequestBody UpdateRaffleRequestDTO request) {
        Long adminId = jwt.getClaim("userId");
        return ResponseEntity.ok(raffleService.updateRaffle(adminId, raffleId, request));
    }

    @PatchMapping("/{raffleId}/activate")
    public ResponseEntity<Void> activateRaffle(@PathVariable Long raffleId) {
        raffleService.activateRaffle(raffleId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{raffleId}/close")
    public ResponseEntity<Void> closeRaffle(@PathVariable Long raffleId) {
        raffleService.closeRaffle(raffleId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("{raffleId}/cancel")
    public ResponseEntity<Void> cancelRaffle(@PathVariable Long raffleId) {
        raffleService.cancelRaffle(raffleId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{raffleId}")
    public ResponseEntity<Void> deleteRaffle(@PathVariable Long raffleId) {
        raffleService.deleteRaffle(raffleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{raffleId}")
    public ResponseEntity<RaffleResponseDTO> getRaffleById(@PathVariable Long raffleId) {
        return ResponseEntity.ok(raffleService.getRaffleResponseDTOById(raffleId));
    }

    @PostMapping("/{raffleId}/draw")
    public ResponseEntity<DrawResultResponseDTO> conductDraw(
            @PathVariable Long raffleId) {

        return ResponseEntity.ok(drawingService.conductDraw(raffleId));
    }

    @GetMapping("/{raffleId}/verify")
    public ResponseEntity<Boolean> verifyDrawIntegrity(@PathVariable Long raffleId) {
        return ResponseEntity.ok(drawingService.verifyDrawIntegrity(raffleId));
    }

    @GetMapping("/count")
    public ResponseEntity<Long> countRafflesByStatus(@RequestParam("status") RaffleStatus status) {
        return ResponseEntity.ok(raffleService.countRafflesByStatus(status));
    }

}
