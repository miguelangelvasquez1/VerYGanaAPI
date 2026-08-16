package com.verygana2.controllers.commercial;

import com.verygana2.dtos.pet.AddCatalogRequestCommentDTO;
import com.verygana2.dtos.pet.CatalogIntegrationRequestDTO;
import com.verygana2.dtos.pet.CatalogRequestCommentDTO;
import com.verygana2.models.enums.CommentAuthorRole;
import com.verygana2.dtos.pet.CatalogIntegrationResponseDTO;
import com.verygana2.dtos.pet.PetImageUploadPermissionDTO;
import com.verygana2.dtos.pet.PetImageUploadRequestDTO;
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
@PreAuthorize("hasRole('COMMERCIAL')")
public class CommercialPetRequestController {

    private final CatalogIntegrationRequestService requestService;

    /**
     * Paso 1 (opcional): pide permiso para subir la imagen del producto.
     *
     * Devuelve una URL pre-firmada contra el bucket de mascotas y la {@code objectKey}
     * que hay que mandar luego como {@code imageObjectKey} al crear la solicitud:
     *
     * <pre>
     *   POST /commercial/pet/requests/image   → { objectKey, uploadUrl, expiresInSeconds }
     *   PUT  &lt;uploadUrl&gt;  (body: el archivo, header Content-Type declarado)
     *   POST /commercial/pet/requests         → { ..., "imageObjectKey": "&lt;objectKey&gt;" }
     * </pre>
     */
    @PostMapping("/image")
    public ResponseEntity<PetImageUploadPermissionDTO> prepareImageUpload(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PetImageUploadRequestDTO dto) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(requestService.prepareImageUpload(userId, dto));
    }

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

    // ── Hilo con el diseñador y el admin ──────────────────────────────────

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<CatalogRequestCommentDTO>> getComments(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(
                requestService.getComments(id, userId, CommentAuthorRole.COMMERCIAL));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CatalogRequestCommentDTO> addComment(
            @PathVariable Long id,
            @Valid @RequestBody AddCatalogRequestCommentDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.status(HttpStatus.CREATED).body(
                requestService.addComment(id, userId, CommentAuthorRole.COMMERCIAL, dto.content()));
    }
}