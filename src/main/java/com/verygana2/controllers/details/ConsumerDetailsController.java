package com.verygana2.controllers.details;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.user.consumer.requests.ConsumerUpdateProfileRequestDTO;
import com.verygana2.dtos.user.consumer.responses.BalanceResponseDTO;
import com.verygana2.dtos.user.consumer.responses.ConsumerInitialDataResponseDTO;
import com.verygana2.dtos.user.consumer.responses.ConsumerProfileResponseDTO;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/consumers")
@RequiredArgsConstructor
public class ConsumerDetailsController {
    
    private final ConsumerDetailsService consumerDetailsService;

    @GetMapping("/balance")
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    public ResponseEntity<BalanceResponseDTO> getConsumerBalance (@AuthenticationPrincipal Jwt jwt){
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(consumerDetailsService.getConsumerBalance(consumerId));
    }

    @GetMapping("/initialData")
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    public ResponseEntity<ConsumerInitialDataResponseDTO> getConsumerInitialData (@AuthenticationPrincipal Jwt jwt){
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(consumerDetailsService.getConsumerInitialData(consumerId));
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    public ResponseEntity<ConsumerProfileResponseDTO> getConsumerProfile (@AuthenticationPrincipal Jwt jwt){
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(consumerDetailsService.getConsumerProfile(consumerId));
    }

    @PutMapping("/profile/edit")
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    public ResponseEntity<EntityUpdatedResponseDTO> updateConsumerProfile (@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConsumerUpdateProfileRequestDTO request){
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(consumerDetailsService.updateConsumerProfile(consumerId, request));
    }
    
}
