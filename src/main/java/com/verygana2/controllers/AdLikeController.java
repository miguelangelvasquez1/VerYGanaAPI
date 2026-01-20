package com.verygana2.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.ad.requests.AdLikeRequest;
import com.verygana2.dtos.ad.responses.AdLikedResponse;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.services.interfaces.AdLikeService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/adLike")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AdLikeController {
    
    private final AdLikeService adLikeService;

    @PostMapping("/like")
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<AdLikedResponse> likeAd(
            @RequestBody AdLikeRequest req,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {

        log.info("Received like request for ad ID: {} and {}", req.getAdId(), req.getSessionUUID());
        Long userId = jwt.getClaim("userId");

        if (userId == null) {
            throw new BusinessException("Token inválido");
        }

        String ipAddress = extractClientIp(request);

        AdLikedResponse response = adLikeService.processAdLike(
            req.getSessionUUID(),
            req.getAdId(),
            userId,
            ipAddress
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/has-liked")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Boolean> hasUserLikedAd(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        
        boolean hasLiked = adLikeService.hasUserLikedAd(id, jwt.getClaim("userId"));
        return ResponseEntity.ok(hasLiked);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");

        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
