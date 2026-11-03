package com.verygana2.controllers.tax;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.tax.TaxProfileDTOs.CatalogEntry;
import com.verygana2.dtos.tax.TaxProfileDTOs.ComplianceTaxProfileResponse;
import com.verygana2.dtos.tax.TaxProfileDTOs.TaxProfileSummary;
import com.verygana2.dtos.tax.TaxProfileDTOs.TaxProfileVersionDetail;
import com.verygana2.dtos.tax.TaxRequestDTOs.CatalogEntryRequest;
import com.verygana2.dtos.tax.TaxRequestDTOs.RetireCatalogEntryRequest;
import com.verygana2.dtos.tax.TaxRequestDTOs.SectionReviewRequest;
import com.verygana2.models.enums.tax.TaxProfileSection;
import com.verygana2.models.enums.tax.TaxProfileStatus;
import com.verygana2.services.interfaces.tax.TaxCatalogService;
import com.verygana2.services.interfaces.tax.TaxProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Revisión de fichas tributarias por sección (lo acreditado frente a lo declarado) y
 * administración del catálogo de códigos tributarios.
 */
@RestController
@RequestMapping("/compliance")
@PreAuthorize("hasRole('COMPLIANCE_OFFICER')")
@RequiredArgsConstructor
public class ComplianceTaxProfileController {

    private final TaxProfileService taxProfileService;
    private final TaxCatalogService catalogService;

    @GetMapping("/tax-profiles")
    public ResponseEntity<PagedResponse<TaxProfileSummary>> list(
            @RequestParam(required = false) TaxProfileStatus status,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(taxProfileService.list(status, pageable));
    }

    @GetMapping("/tax-profiles/{commercialPublicId}")
    public ResponseEntity<ComplianceTaxProfileResponse> get(@PathVariable UUID commercialPublicId) {
        return ResponseEntity.ok(taxProfileService.getForCompliance(commercialPublicId));
    }

    @GetMapping("/tax-profiles/{commercialPublicId}/versions/{versionNumber}")
    public ResponseEntity<TaxProfileVersionDetail> version(@PathVariable UUID commercialPublicId,
            @PathVariable int versionNumber) {
        return ResponseEntity.ok(taxProfileService.getVersionForCompliance(commercialPublicId, versionNumber));
    }

    /** Revisa una sección (a–h) de la versión vigente. Nunca cambia la vocación ni la condición tributaria. */
    @PostMapping("/tax-profiles/{commercialPublicId}/sections/{section}/review")
    public ResponseEntity<ComplianceTaxProfileResponse> reviewSection(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID commercialPublicId, @PathVariable TaxProfileSection section,
            @Valid @RequestBody SectionReviewRequest request) {
        Long reviewerUserId = jwt.getClaim("userId");
        return ResponseEntity.ok(taxProfileService.reviewSection(commercialPublicId, section, reviewerUserId, request));
    }

    // ---- Catálogo tributario ----

    @GetMapping("/tax-catalog")
    public ResponseEntity<List<CatalogEntry>> catalog() {
        return ResponseEntity.ok(catalogService.listAll());
    }

    @PostMapping("/tax-catalog")
    public ResponseEntity<CatalogEntry> createCatalogEntry(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CatalogEntryRequest request) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(catalogService.create(request, userId));
    }

    /** Un código no se edita ni se borra: se retira fijando el fin de su vigencia. */
    @PostMapping("/tax-catalog/{entryId}/retire")
    public ResponseEntity<CatalogEntry> retireCatalogEntry(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long entryId, @Valid @RequestBody RetireCatalogEntryRequest request) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(catalogService.retire(entryId, request, userId));
    }
}
