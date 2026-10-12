package com.verygana2.controllers.marketplace;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.promotions.ActiveKeyCoveragePromotionResponseDTO;
import com.verygana2.dtos.purchase.requests.KeyCoverageQuoteRequestDTO;
import com.verygana2.dtos.purchase.responses.KeyCoverageQuoteResponseDTO;
import com.verygana2.services.interfaces.marketplace.KeyCoveragePromotionService;
import com.verygana2.services.interfaces.marketplace.KeyCoverageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Lo que el consumidor necesita saber sobre el pago con llaves antes de comprar
 * (Contrato B 7.9): cuánto puede cubrir, cuánto le falta y cuánto dinero pagaría.
 */
@RestController
@RequiredArgsConstructor
public class KeyCoverageController {

    private final KeyCoverageService keyCoverageService;
    private final KeyCoveragePromotionService promotionService;

    /** POST /purchases/quote — límites del carrito para pagar con llaves; una llamada por carrito, no por cada cantidad de llaves. */
    @PostMapping("/purchases/quote")
    @PreAuthorize("hasAuthority('ROLE_CONSUMER')")
    public ResponseEntity<KeyCoverageQuoteResponseDTO> quote(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody KeyCoverageQuoteRequestDTO request) {
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(keyCoverageService.quote(consumerId, request));
    }

    /** GET /key-promotions/active — productos con cobertura especial vigente, para anunciarlos en el catálogo. */
    @GetMapping("/key-promotions/active")
    @PreAuthorize("hasAuthority('ROLE_CONSUMER')")
    public ResponseEntity<List<ActiveKeyCoveragePromotionResponseDTO>> activePromotions() {
        return ResponseEntity.ok(promotionService.listActive());
    }
}
