package com.verygana2.controllers.details;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.user.commercial.CommercialInitialDataResponseDTO;
import com.verygana2.dtos.user.commercial.responses.MonthlyReportResponseDTO;
import com.verygana2.models.userDetails.CommercialDetails;
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

    // This method may be used for admin to see a seller info
    @GetMapping("/{commercialId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<CommercialDetails> getSellerById (@PathVariable Long commercialId){
        CommercialDetails commercial = commercialDetailsService.getCommercialById(commercialId);
        return ResponseEntity.ok(commercial);
    }

    @GetMapping("/report")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<MonthlyReportResponseDTO> getMonthlyReport (@AuthenticationPrincipal Jwt jwt, @RequestParam Integer year, @RequestParam Integer month){
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(commercialDetailsService.getMonthlyReport(commercialId, year, month));
        
    }
}