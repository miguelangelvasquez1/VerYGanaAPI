package com.verygana2.controllers.commercial;

import com.verygana2.dtos.pet.CatalogIntegrationRequestDTO;
import com.verygana2.dtos.pet.CatalogIntegrationResponseDTO;
import com.verygana2.services.interfaces.pet.CatalogIntegrationRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/commercial/pet/requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_COMMERCIAL')")
public class CommercialPetRequestController {

    private final CatalogIntegrationRequestService requestService;

    @PostMapping
    public ResponseEntity<CatalogIntegrationResponseDTO> submit(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CatalogIntegrationRequestDTO dto) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.status(HttpStatus.CREATED).body(requestService.submit(userId, dto));
    }

    @GetMapping
    public ResponseEntity<List<CatalogIntegrationResponseDTO>> getMyRequests(
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(requestService.getMyRequests(userId));
    }
}