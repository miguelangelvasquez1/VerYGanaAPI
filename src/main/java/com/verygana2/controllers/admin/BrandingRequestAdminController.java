package com.verygana2.controllers.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.branding.ApproveBrandingRequestDTO;
import com.verygana2.dtos.branding.AssignDesignerDTO;
import com.verygana2.dtos.branding.BrandingRequestDetailDTO;
import com.verygana2.dtos.branding.BrandingRequestSummaryDTO;
import com.verygana2.dtos.branding.GameDesignerSummaryDTO;
import com.verygana2.dtos.branding.RejectBrandingRequestDTO;
import com.verygana2.services.interfaces.BrandingRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/admin/branding-requests")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class BrandingRequestAdminController {

    private final BrandingRequestService brandingRequestService;

    /**
     * Lista todas las solicitudes de branding (todas las marcas).
     * GET /api/admin/branding-requests
     */
    @GetMapping
    public ResponseEntity<List<BrandingRequestSummaryDTO>> getAllBrandingRequests() {
        return ResponseEntity.ok(brandingRequestService.getAllBrandingRequests());
    }

    /**
     * Detalle completo de una solicitud — para revisar y aprobar/rechazar.
     * GET /api/admin/branding-requests/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<BrandingRequestDetailDTO> getBrandingRequestDetail(@PathVariable Long id) {
        return ResponseEntity.ok(brandingRequestService.getAdminBrandingRequestDetail(id));
    }

    /**
     * Lista diseñadores activos — para el selector al aprobar o reasignar.
     * GET /api/admin/branding-requests/designers
     */
    @GetMapping("/designers")
    public ResponseEntity<List<GameDesignerSummaryDTO>> getActiveDesigners() {
        return ResponseEntity.ok(brandingRequestService.getActiveDesigners());
    }

    /**
     * Asigna o reasigna un diseñador a una solicitud ya existente.
     * PATCH /api/admin/branding-requests/{id}/assign-designer
     */
    @PatchMapping("/{id}/assign-designer")
    public ResponseEntity<Void> assignDesigner(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody AssignDesignerDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminUserId = jwt.getClaim("userId");
        brandingRequestService.assignDesigner(id, dto, adminUserId);
        return ResponseEntity.ok().build();
    }

    /**
     * Aprueba la solicitud y asigna un diseñador (PENDING_REVIEW → APPROVED).
     * PATCH /api/admin/branding-requests/{id}/approve
     */
    @PatchMapping("/{id}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable Long id,
            @Valid @RequestBody ApproveBrandingRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminUserId = jwt.getClaim("userId");
        brandingRequestService.approveBrandingRequest(id, dto, adminUserId);
        return ResponseEntity.ok().build();
    }

    /**
     * Rechaza la solicitud con notas (PENDING_REVIEW → REJECTED).
     * PATCH /api/admin/branding-requests/{id}/reject
     */
    @PatchMapping("/{id}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectBrandingRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminUserId = jwt.getClaim("userId");
        brandingRequestService.rejectBrandingRequest(id, dto, adminUserId);
        return ResponseEntity.ok().build();
    }
}
