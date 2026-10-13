package com.verygana2.controllers.commercial;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.promotions.KeyCoveragePromotionOfferResponseDTO;
import com.verygana2.services.interfaces.marketplace.KeyCoveragePromotionService;

import lombok.RequiredArgsConstructor;

/**
 * Invitaciones del empresario a promociones especiales de cobertura con llaves. Nadie
 * participa sin aceptar expresamente (Contrato B 6.22.8), y cada invitación trae las
 * condiciones económicas que estaría aceptando.
 */
@RestController
@RequestMapping("/commercial/key-promotions/offers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COMMERCIAL')")
public class KeyCoveragePromotionOfferController {

    private final KeyCoveragePromotionService promotionService;

    /** GET /commercial/key-promotions/offers */
    @GetMapping
    public ResponseEntity<List<KeyCoveragePromotionOfferResponseDTO>> list(@AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(promotionService.listOffers(commercialId));
    }

    /** POST /commercial/key-promotions/offers/{offerId}/accept */
    @PostMapping("/{offerId}/accept")
    public ResponseEntity<KeyCoveragePromotionOfferResponseDTO> accept(@PathVariable Long offerId,
            @AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(promotionService.acceptOffer(offerId, commercialId));
    }

    /** POST /commercial/key-promotions/offers/{offerId}/reject */
    @PostMapping("/{offerId}/reject")
    public ResponseEntity<KeyCoveragePromotionOfferResponseDTO> reject(@PathVariable Long offerId,
            @AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(promotionService.rejectOffer(offerId, commercialId));
    }

    /** POST /commercial/key-promotions/offers/{offerId}/withdraw — retiro posterior; solo rige hacia adelante. */
    @PostMapping("/{offerId}/withdraw")
    public ResponseEntity<KeyCoveragePromotionOfferResponseDTO> withdraw(@PathVariable Long offerId,
            @AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(promotionService.withdrawOffer(offerId, commercialId));
    }
}
