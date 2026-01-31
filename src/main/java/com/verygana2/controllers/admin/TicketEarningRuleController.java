package com.verygana2.controllers.admin;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.raffle.requests.CreateRuleRequestDTO;
import com.verygana2.dtos.raffle.requests.UpdateRuleRequestDTO;
import com.verygana2.dtos.raffle.responses.RuleResponseDTO;
import com.verygana2.models.enums.raffles.RuleType;
import com.verygana2.services.interfaces.raffles.TicketEarningRuleService;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
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
            @RequestBody @Valid CreateRuleRequestDTO request) {
        return ResponseEntity.ok(ticketEarningRuleService.createTicketEarningRule(request));
    }

    @PutMapping("/{ruleId}")
    public ResponseEntity<EntityUpdatedResponseDTO> updateTicketEarningRule(@PathVariable Long ruleId,
            @RequestBody @Valid UpdateRuleRequestDTO request) {
        return ResponseEntity.ok(ticketEarningRuleService.updateTicketEarningRule(ruleId, request));
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteTicketEarningRule(@PathVariable Long ruleId) {
        ticketEarningRuleService.deleteTicketEarningRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<RuleResponseDTO>> getTicketEarningRulesList(
            @RequestParam(value = "type", required = false) RuleType type,
            @RequestParam(value = "isActive", required = false) boolean isActive,
            @PageableDefault(size = 10, page = 0) Pageable pageable) {
        return ResponseEntity.ok(ticketEarningRuleService.getTicketEarningRulesList(type, isActive, pageable));
    }

    @GetMapping("/{ruleId}")
    public ResponseEntity<RuleResponseDTO> getTicketEarningRule(@PathVariable Long ruleId) {
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
