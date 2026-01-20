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
import com.verygana2.dtos.seller.responses.MonthlyReportResponseDTO;
import com.verygana2.models.userDetails.SellerDetails;
import com.verygana2.services.interfaces.details.SellerDetailsService;

@RestController
@RequestMapping("/sellers")
public class SellerDetailsController {
    
    private final SellerDetailsService sellerDetailsService;

    public SellerDetailsController(SellerDetailsService sellerDetailsService){
        this.sellerDetailsService = sellerDetailsService;
    }

    // This method may be used for admin to see a seller info
    @GetMapping("/{sellerId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<SellerDetails> getSellerById (@PathVariable Long sellerId){
        SellerDetails seller = sellerDetailsService.getSellerById(sellerId);
        return ResponseEntity.ok(seller);
    }

    @GetMapping("/report")
    @PreAuthorize("hasRole('ROLE_SELLER')")
    public ResponseEntity<MonthlyReportResponseDTO> getMonthlyReport (@AuthenticationPrincipal Jwt jwt, @RequestParam Integer year, @RequestParam Integer month){
        Long sellerId = jwt.getClaim("userId");
        return ResponseEntity.ok(sellerDetailsService.getMonthlyReport(sellerId, year, month));
        
    }
}
