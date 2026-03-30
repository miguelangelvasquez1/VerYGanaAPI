package com.verygana2.controllers.details;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.user.commercial.CommercialInitialDataResponseDTO;
import com.verygana2.services.interfaces.details.CommercialDetailsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/commercials")
@RequiredArgsConstructor
public class CommercialDetailsController {
    
    private final CommercialDetailsService commercialDetailsService;

    @GetMapping("/initialData")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<CommercialInitialDataResponseDTO> getCommercialInitialData (@AuthenticationPrincipal Jwt jwt){
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(commercialDetailsService.getCommercialInitialData(commercialId));
    }
}