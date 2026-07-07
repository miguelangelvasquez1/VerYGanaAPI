package com.verygana2.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.branding.AddCommentDTO;
import com.verygana2.dtos.branding.BrandingRequestCommentDTO;
import com.verygana2.dtos.branding.BrandingRequestSummaryDTO;
import com.verygana2.dtos.game.campaign.AssetConfirmRequest;
import com.verygana2.dtos.game.campaign.AssetUploadPermissionDTO;
import com.verygana2.dtos.branding.DesignerBrandingDetailDTO;
import com.verygana2.dtos.user.gamedesigner.ChangePasswordDTO;
import com.verygana2.dtos.user.gamedesigner.GameDesignerProfileResponseDTO;
import com.verygana2.dtos.user.gamedesigner.ResetPasswordByEmailDTO;
import com.verygana2.dtos.user.gamedesigner.UpdateGameDesignerProfileDTO;
import com.verygana2.services.interfaces.GameDesignerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/game-designers")
@RequiredArgsConstructor
@Slf4j
public class GameDesignerController {

    private final GameDesignerService gameDesignerService;

    /**
     * GET /game-designers/me — perfil del diseñador autenticado.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('ROLE_GAME_DESIGNER')")
    public ResponseEntity<GameDesignerProfileResponseDTO> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(gameDesignerService.getMyProfile(userId));
    }

    /**
     * PATCH /game-designers/me — actualiza nombre, apellido y bio.
     */
    @PatchMapping("/me")
    @PreAuthorize("hasRole('ROLE_GAME_DESIGNER')")
    public ResponseEntity<Void> updateProfile(
            @Valid @RequestBody UpdateGameDesignerProfileDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        gameDesignerService.updateProfile(userId, dto);
        return ResponseEntity.ok().build();
    }

    /**
     * PATCH /game-designers/me/password — cambia contraseña (requiere la actual).
     */
    @PatchMapping("/me/password")
    @PreAuthorize("hasRole('ROLE_GAME_DESIGNER')")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        gameDesignerService.changePassword(userId, dto);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /game-designers/password/reset — restablece contraseña sin sesión activa,
     * verificando email + designerCode (el código único asignado por el admin).
     * Este endpoint debe estar permitido sin autenticación en el SecurityConfig.
     */
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordByEmailDTO dto) {
        gameDesignerService.resetPasswordByDesignerCode(dto);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /game-designers/me/branding-requests — lista de solicitudes asignadas al diseñador.
     */
    @GetMapping("/me/branding-requests")
    @PreAuthorize("hasRole('ROLE_GAME_DESIGNER')")
    public ResponseEntity<List<BrandingRequestSummaryDTO>> getAssignedBrandingRequests(
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(gameDesignerService.getAssignedBrandingRequests(userId));
    }

    /**
     * GET /game-designers/me/branding-requests/{id} — detalle de una solicitud asignada.
     */
    @GetMapping("/me/branding-requests/{id}")
    @PreAuthorize("hasRole('ROLE_GAME_DESIGNER')")
    public ResponseEntity<DesignerBrandingDetailDTO> getAssignedBrandingRequestDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(gameDesignerService.getAssignedBrandingRequestDetail(id, userId));
    }

    @PostMapping("/me/assets/upload-url")
    @PreAuthorize("hasRole('ROLE_GAME_DESIGNER')")
    public ResponseEntity<AssetUploadPermissionDTO> generateUploadUrl(
            @Valid @RequestBody FileUploadRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(gameDesignerService.generateUploadUrl(request, userId));
    }

    @PostMapping("/me/assets/confirm")
    @PreAuthorize("hasRole('ROLE_GAME_DESIGNER')")
    public ResponseEntity<Void> confirmUpload(@Valid @RequestBody AssetConfirmRequest request) {
        gameDesignerService.confirmUpload(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me/branding-requests/{id}/preview-url")
    @PreAuthorize("hasRole('ROLE_GAME_DESIGNER')")
    public ResponseEntity<Map<String, String>> getPreviewUrl(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        String url = gameDesignerService.getPreviewUrl(id, userId);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @DeleteMapping("/me/assets/{assetId}")
    @PreAuthorize("hasRole('ROLE_GAME_DESIGNER')")
    public ResponseEntity<Void> deleteAsset(
            @PathVariable Long assetId,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        gameDesignerService.deleteAsset(assetId, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/branding-requests/{id}/draft")
    @PreAuthorize("hasRole('ROLE_GAME_DESIGNER')")
    public ResponseEntity<Void> saveDraftFormData(
            @PathVariable Long id,
            @RequestBody Map<String, Object> formData,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        gameDesignerService.saveDraftFormData(id, userId, formData);
        return ResponseEntity.ok().build();
    }


    @PostMapping("/me/branding-requests/{id}/submit-design")
    @PreAuthorize("hasRole('ROLE_GAME_DESIGNER')")
    public ResponseEntity<Void> submitDesignForReview(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        gameDesignerService.submitDesignForReview(id, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me/branding-requests/{id}/comments")
    @PreAuthorize("hasRole('ROLE_GAME_DESIGNER')")
    public ResponseEntity<List<BrandingRequestCommentDTO>> getComments(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(gameDesignerService.getComments(id, userId));
    }

    @PostMapping("/me/branding-requests/{id}/comments")
    @PreAuthorize("hasRole('ROLE_GAME_DESIGNER')")
    public ResponseEntity<BrandingRequestCommentDTO> addComment(
            @PathVariable Long id,
            @Valid @RequestBody AddCommentDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gameDesignerService.addCommentAsDesigner(id, userId, dto));
    }
}
