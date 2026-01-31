package com.verygana2.controllers.admin;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.raffle.requests.IssueTicketRequestDTO;
import com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleTicketStatus;
import com.verygana2.services.interfaces.raffles.RaffleTicketService;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/tickets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminTicketController {

    private final RaffleTicketService raffleTicketService;

    @PostMapping("/issue")
    public ResponseEntity<List<RaffleTicketResponseDTO>> issueTickets(
            @RequestBody @Valid IssueTicketRequestDTO request) {
        return ResponseEntity.ok(raffleTicketService.issueTickets(request));
    }

    @GetMapping("/raffle/{raffleId}")
    public ResponseEntity<PagedResponse<RaffleTicketResponseDTO>> getTicketsByRaffle(@PathVariable Long raffleId,
            @RequestParam(value = "status", required = false) RaffleTicketStatus status,
            @RequestParam(value = "source", required = false) RaffleTicketSource source,
            @RequestParam(value = "issuedFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime issuedFrom,
            @RequestParam(value = "issuedTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime issuedTo,
            @PageableDefault(size = 20, page = 0) Pageable pageable) {
        return ResponseEntity
                .ok(raffleTicketService.getTicketsByRaffle(raffleId, status, source, issuedFrom, issuedTo, pageable));
    }

    @GetMapping("/consumer/{consumerId}")
    public ResponseEntity<PagedResponse<RaffleTicketResponseDTO>> getUserTickets(@PathVariable Long consumerId,
            @RequestParam(value = "status", required = false) RaffleTicketStatus status,
            @RequestParam(value = "source", required = false) RaffleTicketSource source,
            @RequestParam(value = "issuedFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime issuedFrom,
            @RequestParam(value = "issuedTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime issuedTo,
            @PageableDefault(size = 20, page = 0) Pageable pageable) {
        return ResponseEntity
                .ok(raffleTicketService.getUserTickets(consumerId, status, source, issuedFrom, issuedTo, pageable));
    }

    @GetMapping("/{ticketNumber}/validate")
    public ResponseEntity<Boolean> validateTicket(@PathVariable String ticketNumber) {
        return ResponseEntity.ok(raffleTicketService.validateTicket(ticketNumber));
    }

    @PostMapping("/raffle/{raffleId}/expire")
    public ResponseEntity<Void> expireTickets(@PathVariable Long raffleId) {
        raffleTicketService.expireTickets(raffleId);
        return ResponseEntity.noContent().build();
    }

}
