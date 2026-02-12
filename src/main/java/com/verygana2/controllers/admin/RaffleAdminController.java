package com.verygana2.controllers.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.raffle.requests.CreateRaffleRequestDTO;
import com.verygana2.dtos.raffle.requests.UpdateRaffleRequestDTO;
import com.verygana2.dtos.raffle.responses.DrawResultResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleResponseDTO;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleType;
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

    @PostMapping
    public ResponseEntity<EntityCreatedResponseDTO> createRaffle(@RequestBody @Valid CreateRaffleRequestDTO request) {
        return ResponseEntity.ok(raffleService.createRaffle(request));
    }

    @PutMapping("/{raffleId}")
    public ResponseEntity<EntityUpdatedResponseDTO> updateRaffle(@AuthenticationPrincipal Jwt jwt, @PathVariable Long raffleId,
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

    @GetMapping
    public ResponseEntity<PagedResponse<RaffleResponseDTO>> getRafflesByStatusAndType(
            @RequestParam(value = "status") RaffleStatus status,
            @RequestParam(value = "type") RaffleType type,
            @PageableDefault(size = 10, page = 0, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(raffleService.getRafflesByStatusAndType(status, type, pageable));
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

}
