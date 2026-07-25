package com.verygana2.controllers.commercial;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.product.responses.AllyCommercialResponseDTO;
import com.verygana2.dtos.product.responses.AllyPromotionResponseDTO;
import com.verygana2.services.interfaces.marketplace.AllyPromotionService;

import lombok.RequiredArgsConstructor;

/**
 * Permite a un comercial Premium seleccionar y promocionar productos de comerciales
 * aliados (Básico/Estándar) en el popup final de sus juegos brandeados — ver
 * {@code CAN_PROMOTE_ALLY_PRODUCTS} y {@code GameServiceImpl.getGameRewards}.
 */
@RestController
@RequestMapping("/commercial/allies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_COMMERCIAL')")
public class AllyPromotionController {

    private final AllyPromotionService allyPromotionService;

    @PatchMapping("/promotions/{productId}")
    public ResponseEntity<Void> togglePromotion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productId) {
        Long commercialId = jwt.getClaim("userId");
        allyPromotionService.toggleAllyPromotion(commercialId, productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/promotions")
    public ResponseEntity<List<AllyPromotionResponseDTO>> getMyPromotions(
            @AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(allyPromotionService.getMyPromotions(commercialId));
    }

    /** Vista Premium: comerciales aliados cuyos productos promociono. */
    @GetMapping
    public ResponseEntity<List<AllyCommercialResponseDTO>> getMyAllies(
            @AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(allyPromotionService.getMyAllies(commercialId));
    }

    /** Vista Básico/Estándar: comerciales Premium que me tienen como aliado. */
    @GetMapping("/promoters")
    public ResponseEntity<List<AllyCommercialResponseDTO>> getMyPromoters(
            @AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(allyPromotionService.getMyPromoters(commercialId));
    }
}
