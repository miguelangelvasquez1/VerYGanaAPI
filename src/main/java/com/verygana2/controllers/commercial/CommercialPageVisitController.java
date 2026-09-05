package com.verygana2.controllers.commercial;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.commercial.report.RegisterPageVisitRequestDTO;
import com.verygana2.services.interfaces.commercial.CommercialPageVisitService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Registro de visitas a la página oficial del empresario ("Remisión").
 *
 * <p>El frontend del consumer llama a este endpoint cuando el usuario hace clic en
 * el enlace de un anuncio que redirige al sitio del comercial, e inmediatamente
 * abre la URL. La métrica agregada se consulta en
 * {@code GET /commercials/report/page-visits} (exclusiva Premium).
 */
@RestController
@RequestMapping("/commercials/page-visits")
@RequiredArgsConstructor
public class CommercialPageVisitController {

    private final CommercialPageVisitService pageVisitService;

    @PostMapping
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<Void> registerVisit(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RegisterPageVisitRequestDTO request) {

        pageVisitService.registerVisit(jwt.getClaim("userId"), request);
        return ResponseEntity.noContent().build();
    }
}
