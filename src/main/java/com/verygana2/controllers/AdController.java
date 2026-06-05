package com.verygana2.controllers;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.ad.requests.AdFilterDTO;
import com.verygana2.dtos.ad.requests.AdRejectDTO;
import com.verygana2.dtos.ad.requests.AdUpdateDTO;
import com.verygana2.dtos.ad.requests.CreateAdRequestDTO;
import com.verygana2.dtos.ad.responses.AdAssetUploadPermissionDTO;
import com.verygana2.dtos.ad.responses.AdForAdminDTO;
import com.verygana2.dtos.ad.responses.AdForConsumerDTO;
import com.verygana2.dtos.ad.responses.AdResponseDTO;
import com.verygana2.dtos.ad.responses.AdStatsDTO;
import com.verygana2.dtos.ad.responses.AssetAnalysisResultDTO;
import com.verygana2.dtos.ad.responses.AssetOrphanedResponseDTO;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.services.interfaces.AdService;

import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/ads")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AdController {

    private final AdService adService;

    // ==================== ENDPOINTS PARA ANUNCIANTES ====================

    /**
     * STEP 1 — Prepare.
     * Creates an AdAsset record (PENDING) and returns a pre-signed R2 upload URL.
     * For images, imageDurationSeconds must be provided (5–60 s).
     * For videos, imageDurationSeconds must be null.
     *
     * Frontend flow after this call:
     *   1. Upload the file to R2 using permission.uploadUrl
     *   2. Call POST /ads/assets/{assetId}/analyze
     */
    @PostMapping("/assets/prepare-upload")
    public ResponseEntity<AdAssetUploadPermissionDTO> prepareAssetUpload(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FileUploadRequestDTO request) {
 
        return ResponseEntity.ok(adService.prepareAdAssetUpload(jwt.getClaim("userId"), request));
    }
 
    /**
     * STEP 2 — Analyze.
     * Called after the frontend confirms the file is in R2.
     *
     * For VIDEO: calls ffprobe to get the real duration.
     * For IMAGE: uses the imageDurationSeconds stored in step 1.
     *
     * Transitions: PENDING → ANALYZING → VALIDATED (or ORPHANED on failure).
     *
     * Returns durationSeconds + minPricePerView so the frontend can
     * show the pricing panel to the advertiser.
     *
     * Uses POST (not GET) because it has side effects (DB write + ffprobe call).
     */
    @PostMapping("/assets/{assetId}/analyze")
    public ResponseEntity<AssetAnalysisResultDTO> analyzeAsset(
            @PathVariable Long assetId,
            @AuthenticationPrincipal Jwt jwt
        ) {
 
        return ResponseEntity.ok(adService.analyzeAsset(assetId, jwt.getClaim("userId")));
    }
 
    /**
     * STEP 2.5 — Orphan.
     * Marks an asset as ORPHANED so the cleanup job removes it from R2.
     *
     * Called when:
     *  - User changes the selected file (old asset becomes unused)
     *  - User cancels the form
     *  - Browser fires beforeunload (via navigator.sendBeacon)
     *
     * Uses POST + path variable (no body) so sendBeacon can call it.
     * sendBeacon cannot send a body reliably across all browsers.
     */
    @PostMapping("/assets/orphan")
    public ResponseEntity<AssetOrphanedResponseDTO> orphanAsset(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Long> body) {

        Long assetId = body.get("assetId");

        if (assetId == null) {
            throw new ValidationException("assetId es requerido");
        }
 
        return ResponseEntity.ok(adService.markAssetAsOrphaned(jwt.getClaim("userId"), assetId));
    }
 
    /**
     * STEP 3 — Create.
     * Creates the Ad entity after the advertiser has confirmed pricing.
     * Asset must be in VALIDATED state (analyze must have succeeded).
     */
    @PostMapping
    public ResponseEntity<Void> createAd(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateAdRequestDTO request) {
 
        adService.createAdWithAsset(jwt.getClaim("userId"), request);
        return ResponseEntity.status(201).build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COMMERCIAL')")
    public ResponseEntity<AdResponseDTO> updateAd(
            @PathVariable Long id,
            @Valid @RequestBody AdUpdateDTO updateDto,
            @AuthenticationPrincipal Jwt jwt) {
        
        AdResponseDTO ad = adService.updateAd(id, updateDto, jwt.getClaim("userId"));
        return ResponseEntity.ok(ad);
    }

    @GetMapping("/my-ads/filter")
    @PreAuthorize("hasRole('COMMERCIAL')")
    public ResponseEntity<PagedResponse<AdResponseDTO>> getFilteredAds(
            @AuthenticationPrincipal Jwt jwt,
            @ModelAttribute AdFilterDTO filters,
            Pageable pageable) {

        PagedResponse<AdResponseDTO> ads = adService.getFilteredAds(jwt.getClaim("userId"), filters, pageable);
        return ResponseEntity.ok(ads);
    }

    @GetMapping("/{adId}/details")
    @PreAuthorize("hasRole('COMMERCIAL')")
    public ResponseEntity<AdResponseDTO> getAdDetails(
        @PathVariable Long adId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(adService.getAdDetails(adId, jwt.getClaim("userId")));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('COMMERCIAL')")
    public ResponseEntity<AdResponseDTO> activateAdAsCommercial(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        
        AdResponseDTO ad = adService.activateAdAsCommercial(id, jwt.getClaim("userId"));
        return ResponseEntity.ok(ad);
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasRole('COMMERCIAL')")
    public ResponseEntity<AdResponseDTO> pauseAdAsCommrcial(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        
        AdResponseDTO ad = adService.pauseAdAsCommercial(id, jwt.getClaim("userId"));
        return ResponseEntity.ok(ad);
    }

    // ==================== ENDPOINTS PARA USUARIOS CONSUMER ====================

    @GetMapping("/next")
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<AdForConsumerDTO> getNextAd(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long consumerId = jwt.getClaim("userId");

        return adService.getNextAdForConsumer(consumerId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/user/available/count")
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<Long> countAvailableAdsForUser(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = jwt.getClaim("userId");
        long count = adService.countAvailableAdsForUser(userId);
        
        log.debug("Usuario {} tiene {} anuncios disponibles", userId, count);
        
        return ResponseEntity.ok(count);
    }

    //Stats anunciantes

    @GetMapping("/{id}/stats")
    @PreAuthorize("hasRole('COMMERCIAL')")
    public ResponseEntity<AdStatsDTO> getAdStats(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        
        AdStatsDTO stats = adService.getAdStats(id, jwt.getClaim("userId"));
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/my-stats")
    @PreAuthorize("hasRole('COMMERCIAL')")
    public ResponseEntity<AdStatsDTO> getCommercialStats(
            @AuthenticationPrincipal Jwt jwt) {
        
        AdStatsDTO stats = adService.getCommercialStats(jwt.getClaim("userId"));
        return ResponseEntity.ok(stats);
    }

    // ==================== ENDPOINTS PARA ADMINISTRADORES ====================

    @PostMapping("/admin/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdResponseDTO> activateAdAsAdmin(
            @PathVariable Long id) {
        
        AdResponseDTO ad = adService.activateAdAsAdmin(id);
        return ResponseEntity.ok(ad);
    }

    @PostMapping("/admin/{id}/pause")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdResponseDTO> pauseAdAsAdmin(
            @PathVariable Long id) {
        
        AdResponseDTO ad = adService.pauseAdAsAdmin(id);
        return ResponseEntity.ok(ad);
    }

    @PostMapping("/admin/{id}/block")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdResponseDTO> blockAdAsAdmin(
            @PathVariable Long id) {
        
        AdResponseDTO ad = adService.blockAdAsAdmin(id);
        return ResponseEntity.ok(ad);
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedResponse<AdForAdminDTO>> getAllAdsForAdmin(
        @RequestParam(required = false) AdStatus status,
        Pageable pageable) {
        
        Page<AdForAdminDTO> ads = adService.getAdsByStatus(status, pageable);
        return ResponseEntity.ok(PagedResponse.from(ads));
    }

    @PostMapping("/admin/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdResponseDTO> approveAd(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        
        AdResponseDTO ad = adService.approveAd(id, jwt.getClaim("userId"));
        return ResponseEntity.ok(ad);
    }

    @PostMapping("/admin/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdResponseDTO> rejectAd(
            @PathVariable Long id,
            @RequestBody @Valid AdRejectDTO rejectDto,
            @AuthenticationPrincipal Jwt jwt) {
        
        AdResponseDTO ad = adService.rejectAd(
            id, 
            rejectDto.getReason(), 
            jwt.getClaim("userId")
        );
        return ResponseEntity.ok(ad);
    }

    // ==================== UTILIDADES ====================

    // private String getClientIpAddress(HttpServletRequest request) {
    //     String[] headerNames = {
    //         "X-Forwarded-For",
    //         "X-Real-IP",
    //         "Proxy-Client-IP",
    //         "WL-Proxy-Client-IP",
    //         "HTTP_X_FORWARDED_FOR",
    //         "HTTP_X_FORWARDED",
    //         "HTTP_X_CLUSTER_CLIENT_IP",
    //         "HTTP_CLIENT_IP",
    //         "HTTP_FORWARDED_FOR",
    //         "HTTP_FORWARDED",
    //         "HTTP_VIA",
    //         "REMOTE_ADDR"
    //     };

    //     for (String header : headerNames) {
    //         String ip = request.getHeader(header);
    //         if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
    //             // Tomar la primera IP si hay múltiples
    //             return ip.split(",")[0].trim();
    //         }
    //     }

    //     return request.getRemoteAddr();
    // }
}