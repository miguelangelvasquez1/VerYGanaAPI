package com.verygana2.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.ad.requests.AdCreateDTO;
import com.verygana2.dtos.ad.requests.AdFilterDTO;
import com.verygana2.dtos.ad.requests.AdRejectDTO;
import com.verygana2.dtos.ad.requests.AdUpdateDTO;
import com.verygana2.dtos.ad.responses.AdForAdminDTO;
import com.verygana2.dtos.ad.responses.AdForConsumerDTO;
import com.verygana2.dtos.ad.responses.AdResponseDTO;
import com.verygana2.dtos.ad.responses.AdStatsDTO;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.services.interfaces.AdService;

import jakarta.validation.Valid;
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

    @PostMapping
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<AdResponseDTO> createAd(
            @Valid @RequestPart("ad") AdCreateDTO createDto,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        
        log.info("Creating ad for advertiser: {}" + jwt.getClaim("userId"));
        AdResponseDTO ad = adService.createAd(createDto, file, jwt.getClaim("userId"));
        
        return ResponseEntity.status(HttpStatus.CREATED).body(ad);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<AdResponseDTO> updateAd(
            @PathVariable Long id,
            @Valid @RequestPart("ad") AdUpdateDTO updateDto,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        
        AdResponseDTO ad = adService.updateAd(id, updateDto, file, jwt.getClaim("userId"));
        return ResponseEntity.ok(ad);
    }

    @GetMapping("/my-ads/filter")
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<PagedResponse<AdResponseDTO>> getFilteredAds(
            @AuthenticationPrincipal Jwt jwt,
            @ModelAttribute AdFilterDTO filters,
            Pageable pageable) {

        PagedResponse<AdResponseDTO> ads = adService.getFilteredAds(jwt.getClaim("userId"), filters, pageable);
        return ResponseEntity.ok(ads);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<AdResponseDTO> activateAdAsAdvertiser(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        
        AdResponseDTO ad = adService.activateAdAsAdvertiser(id, jwt.getClaim("userId"));
        return ResponseEntity.ok(ad);
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<AdResponseDTO> pauseAdAsAdvertiser(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        
        AdResponseDTO ad = adService.pauseAdAsAdvertiser(id, jwt.getClaim("userId"));
        return ResponseEntity.ok(ad);
    }

    // ==================== ENDPOINTS PARA USUARIOS CONSUMER ====================

    @GetMapping("/user/available")
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<PagedResponse<AdForConsumerDTO>> getAvailableAdsForUser(
            @AuthenticationPrincipal Jwt jwt,
            Pageable pageable) { //sortBy puede ser por currentLikes para equilibrar
        
        Long userId = jwt.getClaim("userId");
        PagedResponse<AdForConsumerDTO> ads = adService.getAvailableAdsForUser(userId, pageable);
        
        log.info("Se retornaron {} anuncios de {} totales para usuario {}", 
                 ads.getMeta().getTotalElements(), ads.getMeta().getTotalElements(), userId);
        
        return ResponseEntity.ok(ads);
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
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<AdStatsDTO> getAdStats(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        
        AdStatsDTO stats = adService.getAdStats(id, jwt.getClaim("userId"));
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/my-stats")
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<AdStatsDTO> getAdvertiserStats(
            @AuthenticationPrincipal Jwt jwt) {
        
        AdStatsDTO stats = adService.getAdvertiserStats(jwt.getClaim("userId"));
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

    @GetMapping("/admin/pending") //Quitar este endpoint
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedResponse<AdForAdminDTO>> getPendingAds(
            Pageable pageable) {
        
        Page<AdForAdminDTO> ads = adService.getPendingApprovalAds(pageable);
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