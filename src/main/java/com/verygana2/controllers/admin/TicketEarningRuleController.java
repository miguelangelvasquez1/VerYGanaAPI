package com.verygana2.controllers.admin;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import com.verygana2.dtos.raffle.requests.CreateTicketEarningRuleRequestDTO;
import com.verygana2.dtos.raffle.requests.UpdateTicketEarningRuleRequestDTO;
import com.verygana2.dtos.raffle.responses.TicketEarningRuleResponseDTO;
import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.services.interfaces.raffles.TicketEarningRuleService;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/ticket-rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class TicketEarningRuleController {

    private final TicketEarningRuleService ticketEarningRuleService;

    @PostMapping
    public ResponseEntity<EntityCreatedResponseDTO> createTicketEarningRule(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid CreateTicketEarningRuleRequestDTO request) {
        Long adminId = jwt.getClaim("userId");
        return ResponseEntity.ok(ticketEarningRuleService.createTicketEarningRule(adminId, request));
    }

    @PutMapping("/{ruleId}")
    public ResponseEntity<EntityUpdatedResponseDTO> updateTicketEarningRule(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long ruleId,
            @RequestBody @Valid UpdateTicketEarningRuleRequestDTO request) {
        Long adminId = jwt.getClaim("userId");
        return ResponseEntity.ok(ticketEarningRuleService.updateTicketEarningRule(adminId, ruleId, request));
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteTicketEarningRule(@PathVariable Long ruleId) {
        ticketEarningRuleService.deleteTicketEarningRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<TicketEarningRuleResponseDTO>> getTicketEarningRulesList(
            @RequestParam(value = "type", required = false) TicketEarningRuleType type,
            @RequestParam(value = "isActive", required = false) Boolean isActive,
            @PageableDefault(size = 10, page = 0) Pageable pageable) {
        return ResponseEntity.ok(ticketEarningRuleService.getTicketEarningRulesList(type, isActive, pageable));
    }

    @GetMapping("/{ruleId}")
    public ResponseEntity<TicketEarningRuleResponseDTO> getTicketEarningRule(@PathVariable Long ruleId) {
        return ResponseEntity.ok(ticketEarningRuleService.getTicketEarningRuleResponseDTOById(ruleId));
    }

    @PatchMapping("/{ruleId}/activate")
    public ResponseEntity<Void> activeTicketEarningRule(@PathVariable Long ruleId) {
        ticketEarningRuleService.activateTicketEarningRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{ruleId}/deactivate")
    public ResponseEntity<Void> deactiveTicketEarningRule(@PathVariable Long ruleId) {
        ticketEarningRuleService.deactivateTicketEarningRule(ruleId);
        return ResponseEntity.noContent().build();
    }
}
