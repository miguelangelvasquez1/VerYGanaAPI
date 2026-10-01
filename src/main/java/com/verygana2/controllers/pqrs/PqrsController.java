package com.verygana2.controllers.pqrs;

import java.io.IOException;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.pqrs.requests.CreatePqrsRequestDTO;
import com.verygana2.dtos.pqrs.requests.PreparePqrsAssetRequestDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAssetResponseDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAssetUploadPermissionDTO;
import com.verygana2.dtos.pqrs.responses.PqrsResponseDTO;
import com.verygana2.services.interfaces.pqrs.PqrsAssetService;
import com.verygana2.services.interfaces.pqrs.PqrsService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/pqrs")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class PqrsController {

    private final PqrsService pqrsService;
    private final PqrsAssetService pqrsAssetService;

    /**
     * POST /pqrs/assets/prepare-upload — evidencia (foto/video) opcional que
     * se sube ANTES de radicar el PQRS (mismo patrón prepare→confirm que
     * ProductController: la subida va directo a R2, el backend nunca recibe
     * los bytes). El id devuelto se referencia luego en CreatePqrsRequestDTO
     * / ReportPurchaseItemRequestDTO (assetIds).
     */
    @PostMapping("/assets/prepare-upload")
    public ResponseEntity<PqrsAssetUploadPermissionDTO> prepareAssetUpload(
            @Valid @RequestBody PreparePqrsAssetRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(pqrsAssetService.prepareUpload(userId, request));
    }

    /**
     * POST /pqrs/assets/{id}/confirm — confirma que el archivo ya se subió a
     * R2; valida tamaño/mime real antes de dejarlo disponible para adjuntar.
     */
    @PostMapping("/assets/{id}/confirm")
    public ResponseEntity<PqrsAssetResponseDTO> confirmAssetUpload(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(pqrsAssetService.confirmUpload(userId, id));
    }

    /**
     * GET /pqrs/assets/{id}/view — hace streaming directo del archivo desde
     * R2 (sin URL prefirmada), mismo patrón que
     * ProductController.getPrivateProductImage: evita depender de CORS
     * configurado en el bucket para que el navegador pueda renderizarlo en
     * un &lt;img&gt;/&lt;video&gt;. Soporta JWT vía header Authorization o
     * query param ?token= (para tags de imagen/video, que no pueden mandar
     * headers). Solo el dueño del archivo o un admin pueden verlo.
     */
    @GetMapping("/assets/{id}/view")
    public void streamAsset(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse response) throws IOException {
        Long userId = jwt.getClaim("userId");
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        pqrsAssetService.streamAsset(id, userId, isAdmin, response);
    }

    /**
     * POST /pqrs — cualquier usuario autenticado radica una petición, queja, reclamo o sugerencia.
     */
    @PostMapping
    public ResponseEntity<PqrsResponseDTO> createPqrs(
            @Valid @RequestBody CreatePqrsRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        PqrsResponseDTO response = pqrsService.createPqrs(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /pqrs/mine — PQRS radicados (based) por el usuario autenticado.
     */
    @GetMapping("/mine")
    public ResponseEntity<PagedResponse<PqrsResponseDTO>> getMyPqrs(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable) {

        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(pqrsService.getMyPqrs(userId, pageable));
    }

    /**
     * GET /pqrs/{id} — detalle de un PQRS propio.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PqrsResponseDTO> getMyPqrsDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(pqrsService.getMyPqrsDetail(id, userId));
    }
}
