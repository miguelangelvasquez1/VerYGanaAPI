package com.VerYGana.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.VerYGana.dtos.ad.requests.AdApprovalDTO;
import com.VerYGana.dtos.ad.requests.AdCreateDTO;
import com.VerYGana.dtos.ad.requests.AdUpdateDTO;
import com.VerYGana.dtos.ad.responses.AdResponseDTO;
import com.VerYGana.dtos.ad.responses.AdStatsDTO;
import com.VerYGana.models.enums.AdStatus;
import com.VerYGana.models.enums.Preference;
import com.VerYGana.services.interfaces.AdService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AdController {

    private final AdService adService;

    // ==================== ENDPOINTS PÚBLICOS ====================

    @GetMapping("/available")
    public ResponseEntity<Page<AdResponseDTO>> getAvailableAds(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") 
            ? Sort.by(sortBy).ascending() 
            : Sort.by(sortBy).descending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<AdResponseDTO> ads = adService.getAvailableAds(pageable);
        
        return ResponseEntity.ok(ads);
    }

    @GetMapping("/available/category/{category}")
    public ResponseEntity<Page<AdResponseDTO>> getAvailableAdsByCategory(
            @PathVariable Preference category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AdResponseDTO> ads = adService.getAvailableAdsByCategory(category, pageable);
        
        return ResponseEntity.ok(ads);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdResponseDTO> getAdById(@PathVariable Long id) {
        AdResponseDTO ad = adService.getAdById(id);
        return ResponseEntity.ok(ad);
    }

    @GetMapping("/top")
    public ResponseEntity<Page<AdResponseDTO>> getTopAds(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<AdResponseDTO> ads = adService.getTopAdsByLikes(pageable);
        
        return ResponseEntity.ok(ads);
    }

    // ==================== ENDPOINTS PARA USUARIOS AUTENTICADOS ====================

    @PostMapping("/{id}/like")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AdResponseDTO> likeAd(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        
        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        
        AdResponseDTO ad = adService.processAdLike(
            id, 
            jwt.getClaim("userId"), 
            ipAddress, 
            userAgent
        );
        
        return ResponseEntity.ok(ad);
    }

    @GetMapping("/user/available")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<AdResponseDTO>> getAvailableAdsForUser(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AdResponseDTO> ads = adService.getAvailableAdsForUser(
            jwt.getClaim("userId"), 
            pageable
        );
        
        return ResponseEntity.ok(ads);
    }

    @GetMapping("/{id}/has-liked")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Boolean> hasUserLikedAd(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        
        boolean hasLiked = adService.hasUserLikedAd(id, jwt.getClaim("userId"));
        return ResponseEntity.ok(hasLiked);
    }

    // ==================== ENDPOINTS PARA ANUNCIANTES ====================

    @PostMapping
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<AdResponseDTO> createAd(
            @Valid @RequestBody AdCreateDTO createDto,
            @AuthenticationPrincipal Jwt jwt) {
        
        log.info("Creating ad for advertiser: {}" + jwt.getClaim("userId"));
        AdResponseDTO ad = adService.createAd(createDto, jwt.getClaim("userId"));
        
        return ResponseEntity.status(HttpStatus.CREATED).body(ad);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<AdResponseDTO> updateAd(
            @PathVariable Long id,
            @Valid @RequestBody AdUpdateDTO updateDto,
            @AuthenticationPrincipal Jwt jwt) {
        
        AdResponseDTO ad = adService.updateAd(id, updateDto, jwt.getClaim("userId"));
        return ResponseEntity.ok(ad);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<Void> deleteAd(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        
        adService.deleteAd(id, jwt.getClaim("userId"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my-ads")
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<Page<AdResponseDTO>> getMyAds(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") 
            ? Sort.by(sortBy).ascending() 
            : Sort.by(sortBy).descending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<AdResponseDTO> ads = adService.getAdsByAdvertiser(jwt.getClaim("userId"), pageable);
        
        return ResponseEntity.ok(ads);
    }

    @GetMapping("/my-ads/status/{status}")
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<Page<AdResponseDTO>> getMyAdsByStatus(
            @PathVariable AdStatus status,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AdResponseDTO> ads = adService.getAdsByAdvertiserAndStatus(
            jwt.getClaim("userId"), 
            status, 
            pageable
        );
        
        return ResponseEntity.ok(ads);
    }

    @GetMapping("/my-ads/search")
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<Page<AdResponseDTO>> searchMyAds(
            @RequestParam String query,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AdResponseDTO> ads = adService.searchAdvertiserAds(
            jwt.getClaim("userId"), 
            query, 
            pageable
        );
        
        return ResponseEntity.ok(ads);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<AdResponseDTO> activateAd(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        
        AdResponseDTO ad = adService.activateAd(id, jwt.getClaim("userId"));
        return ResponseEntity.ok(ad);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<AdResponseDTO> deactivateAd(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        
        AdResponseDTO ad = adService.deactivateAd(id, jwt.getClaim("userId"));
        return ResponseEntity.ok(ad);
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<AdResponseDTO> pauseAd(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        
        AdResponseDTO ad = adService.pauseAd(id, jwt.getClaim("userId"));
        return ResponseEntity.ok(ad);
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasRole('ADVERTISER')")
    public ResponseEntity<AdResponseDTO> resumeAd(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        
        AdResponseDTO ad = adService.resumeAd(id, jwt.getClaim("userId"));
        return ResponseEntity.ok(ad);
    }

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

    @GetMapping("/admin/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AdResponseDTO>> getPendingAds(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<AdResponseDTO> ads = adService.getPendingApprovalAds(pageable);
        
        return ResponseEntity.ok(ads);
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
            @RequestBody @Valid AdApprovalDTO approvalDto,
            @AuthenticationPrincipal Jwt jwt) {
        
        AdResponseDTO ad = adService.rejectAd(
            id, 
            approvalDto.getReason(), 
            jwt.getClaim("userId")
        );
        return ResponseEntity.ok(ad);
    }

    // ==================== UTILIDADES ====================

    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Tomar la primera IP si hay múltiples
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }
}