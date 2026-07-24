package com.verygana2.controllers.raffles;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.raffle.responses.DrawStatusResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleSummaryResponseDTO;
import com.verygana2.dtos.raffle.responses.UserRaffleSummaryResponseDTO;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.services.interfaces.raffles.RaffleService;
import com.verygana2.services.interfaces.raffles.WaitingRoomService;
import com.verygana2.services.raffles.RaffleDrawStateCache;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/raffles")
@RequiredArgsConstructor
public class RaffleController {

    private final RaffleService raffleService;
    private final RaffleDrawStateCache drawStateCache;
    private final WaitingRoomService waitingRoomService;

    // Para admin
    @GetMapping
    public ResponseEntity<PagedResponse<RaffleSummaryResponseDTO>> getSummaryRafflesByFilters(
            @RequestParam(required = false) RaffleStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDate drawDate,
            @RequestParam(required = false) RaffleType type,
            @PageableDefault(size = 10, page = 0, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(raffleService.getSummaryRafflesByFilters(status, search, drawDate, type, pageable));
    }

    @GetMapping("/{raffleId}")
    public ResponseEntity<RaffleResponseDTO> getRaffleById(@PathVariable Long raffleId) {
        return ResponseEntity.ok(raffleService.getRaffleResponseDTOById(raffleId));
    }

    // Para usuarios
    @GetMapping("/lives")
    public ResponseEntity<List<RaffleSummaryResponseDTO>> getLiveRaffles(
            @RequestParam(required = false) String municipalityCode) {
        return ResponseEntity.ok(raffleService.getLiveRaffles(municipalityCode));
    }

    @GetMapping("/actives")
    public ResponseEntity<PagedResponse<RaffleSummaryResponseDTO>> getActiveRaffles(
            @RequestParam(name = "type", required = false) RaffleType type,
            @RequestParam(required = false) String municipalityCode,
            @RequestParam("pageNumber") int pageNumber) {
        return ResponseEntity.ok(raffleService.getActiveRaffles(type, municipalityCode, pageNumber));
    }

    @GetMapping("/{raffleId}/draw-status")
    public ResponseEntity<DrawStatusResponseDTO> getDrawStatus(
            @PathVariable Long raffleId) {

        Raffle raffle = raffleService.getRaffleById(raffleId);

        ZonedDateTime now = ZonedDateTime.now(ZoneId.from(ZoneOffset.UTC));
        long secondsUntilDraw = ChronoUnit.SECONDS.between(now, raffle.getDrawDate());
        long secondsUntilDrawClamped = Math.max(0, secondsUntilDraw);

        int viewerCount = waitingRoomService.getViewerCount(raffleId);

        DrawStatusResponseDTO status = drawStateCache.buildStatus(
                raffleId,
                viewerCount,
                secondsUntilDrawClamped,
                raffle,
                raffle.getTotalParticipants());

        return ResponseEntity.ok(status);
    }

    @GetMapping("/me")
    public ResponseEntity<PagedResponse<UserRaffleSummaryResponseDTO>> getMyRafflesByStatus (@AuthenticationPrincipal Jwt jwt, @RequestParam RaffleStatus status, @PageableDefault(size = 10, page = 0) Pageable pageable){
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(raffleService.getMyRafflesByStatus(consumerId, status, pageable));
    }

    @GetMapping("/me/count")
    public ResponseEntity<Long> countMyRafflesByStatus (@AuthenticationPrincipal Jwt jwt, @RequestParam RaffleStatus status) {
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(raffleService.countMyRafflesByStatus(consumerId, status));
    }

}
