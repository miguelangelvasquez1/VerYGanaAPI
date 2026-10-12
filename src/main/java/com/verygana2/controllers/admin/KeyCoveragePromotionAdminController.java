package com.verygana2.controllers.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.promotions.KeyCoveragePromotionAddOfferRequestDTO;
import com.verygana2.dtos.promotions.KeyCoveragePromotionRequestDTO;
import com.verygana2.dtos.promotions.KeyCoveragePromotionResponseDTO;
import com.verygana2.services.interfaces.marketplace.KeyCoveragePromotionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Administración de las promociones especiales de cobertura con llaves (Contrato B
 * 6.22.8, 7.9.2): borrador, ofertas invitadas, autorización con garantía, pausa y cierre.
 */
@RestController
@RequestMapping("/admin/key-promotions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class KeyCoveragePromotionAdminController {

    private final KeyCoveragePromotionService promotionService;

    /** GET /admin/key-promotions?page=0&size=20 */
    @GetMapping
    public ResponseEntity<PagedResponse<KeyCoveragePromotionResponseDTO>> list(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(promotionService.list(pageable)));
    }

    /** GET /admin/key-promotions/{id} — incluye presupuesto consumido y estado de cada oferta. */
    @GetMapping("/{id}")
    public ResponseEntity<KeyCoveragePromotionResponseDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(promotionService.get(id));
    }

    /** POST /admin/key-promotions — crea un borrador. */
    @PostMapping
    public ResponseEntity<KeyCoveragePromotionResponseDTO> create(
            @Valid @RequestBody KeyCoveragePromotionRequestDTO request) {
        return ResponseEntity.ok(promotionService.create(request));
    }

    /** PUT /admin/key-promotions/{id} — solo mientras es borrador. */
    @PutMapping("/{id}")
    public ResponseEntity<KeyCoveragePromotionResponseDTO> update(@PathVariable Long id,
            @Valid @RequestBody KeyCoveragePromotionRequestDTO request) {
        return ResponseEntity.ok(promotionService.updateDraft(id, request));
    }

    /** POST /admin/key-promotions/{id}/offers — invita un producto de una Empresa Tipo B. */
    @PostMapping("/{id}/offers")
    public ResponseEntity<KeyCoveragePromotionResponseDTO> addOffer(@PathVariable Long id,
            @Valid @RequestBody KeyCoveragePromotionAddOfferRequestDTO request) {
        return ResponseEntity.ok(promotionService.addOffer(id, request.productId()));
    }

    /** DELETE /admin/key-promotions/{id}/offers/{offerId} — solo mientras es borrador. */
    @DeleteMapping("/{id}/offers/{offerId}")
    public ResponseEntity<KeyCoveragePromotionResponseDTO> removeOffer(@PathVariable Long id,
            @PathVariable Long offerId) {
        return ResponseEntity.ok(promotionService.removeOffer(id, offerId));
    }

    /** POST /admin/key-promotions/{id}/authorize — aparta el presupuesto desde FORTIFICATION. */
    @PostMapping("/{id}/authorize")
    public ResponseEntity<KeyCoveragePromotionResponseDTO> authorize(@PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long adminId = jwt.getClaim("userId");
        return ResponseEntity.ok(promotionService.authorize(id, adminId));
    }

    /** POST /admin/key-promotions/{id}/pause */
    @PostMapping("/{id}/pause")
    public ResponseEntity<KeyCoveragePromotionResponseDTO> pause(@PathVariable Long id) {
        return ResponseEntity.ok(promotionService.pause(id));
    }

    /** POST /admin/key-promotions/{id}/resume */
    @PostMapping("/{id}/resume")
    public ResponseEntity<KeyCoveragePromotionResponseDTO> resume(@PathVariable Long id) {
        return ResponseEntity.ok(promotionService.resume(id));
    }

    /** POST /admin/key-promotions/{id}/end — cierre definitivo; devuelve la garantía no usada. */
    @PostMapping("/{id}/end")
    public ResponseEntity<KeyCoveragePromotionResponseDTO> end(@PathVariable Long id) {
        return ResponseEntity.ok(promotionService.end(id));
    }
}
