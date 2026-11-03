package com.verygana2.controllers.tax;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.tax.TaxProfileDTOs.CatalogEntry;
import com.verygana2.dtos.tax.TaxProfileDTOs.Establishment;
import com.verygana2.dtos.tax.TaxProfileDTOs.TaxProfileResponse;
import com.verygana2.dtos.tax.TaxProfileDTOs.TaxProfileVersionDetail;
import com.verygana2.dtos.tax.TaxProfileDTOs.VersionSummary;
import com.verygana2.dtos.tax.TaxProfileSubmitRequestDTO;
import com.verygana2.dtos.tax.TaxRequestDTOs.CloseEstablishmentRequest;
import com.verygana2.dtos.tax.TaxRequestDTOs.EstablishmentRequest;
import com.verygana2.security.auth.RequestClientInfo;
import com.verygana2.services.interfaces.tax.CommercialEstablishmentService;
import com.verygana2.services.interfaces.tax.TaxCatalogService;
import com.verygana2.services.interfaces.tax.TaxProfileService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Ficha tributaria del empresario (4.7 a–h) y sus establecimientos. Es información
 * declarada: enviarla no acepta ninguna declaración ni cambia la vocación o la comisión.
 */
@RestController
@RequestMapping("/commercials")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COMMERCIAL')")
public class CommercialTaxProfileController {

    private final TaxProfileService taxProfileService;
    private final TaxCatalogService catalogService;
    private final CommercialEstablishmentService establishmentService;

    /** Ficha vigente. 204 si todavía no la ha enviado. */
    @GetMapping("/tax-profile")
    public ResponseEntity<TaxProfileResponse> get(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        TaxProfileResponse profile = taxProfileService.getOwn(userId);
        return profile == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(profile);
    }

    /** Envía la ficha: cada envío crea una versión nueva y la anterior queda en el historial. */
    @PutMapping("/tax-profile")
    public ResponseEntity<TaxProfileResponse> submit(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TaxProfileSubmitRequestDTO request, HttpServletRequest httpRequest) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(taxProfileService.submit(userId, request,
                RequestClientInfo.resolveIp(httpRequest), RequestClientInfo.resolveUserAgent(httpRequest)));
    }

    @GetMapping("/tax-profile/versions")
    public ResponseEntity<List<VersionSummary>> versions(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(taxProfileService.listVersions(userId));
    }

    @GetMapping("/tax-profile/versions/{versionNumber}")
    public ResponseEntity<TaxProfileVersionDetail> version(@AuthenticationPrincipal Jwt jwt,
            @PathVariable int versionNumber) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(taxProfileService.getVersion(userId, versionNumber));
    }

    /** Códigos vigentes para los selectores de la ficha. */
    @GetMapping("/tax-profile/catalog")
    public ResponseEntity<List<CatalogEntry>> catalog() {
        return ResponseEntity.ok(catalogService.listActive());
    }

    // ---- Establecimientos (inciso f) ----

    @GetMapping("/establishments")
    public ResponseEntity<List<Establishment>> establishments(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(establishmentService.list(userId));
    }

    @PostMapping("/establishments")
    public ResponseEntity<Establishment> createEstablishment(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EstablishmentRequest request) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(establishmentService.create(userId, request));
    }

    /** Los establecimientos no se borran: se cierran con un motivo y quedan en el historial. */
    @PostMapping("/establishments/{establishmentId}/close")
    public ResponseEntity<Establishment> closeEstablishment(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long establishmentId, @Valid @RequestBody CloseEstablishmentRequest request) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(establishmentService.close(userId, establishmentId, request));
    }
}
