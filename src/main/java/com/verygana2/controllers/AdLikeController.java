package com.verygana2.controllers;

import org.springframework.data.domain.PageRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.ad.requests.AdLikeRequest;
import com.verygana2.dtos.ad.responses.AdForConsumerDTO;
import com.verygana2.dtos.ad.responses.AdLikeResponseDTO;
import com.verygana2.dtos.ad.responses.AdLikedResponse;
import com.verygana2.dtos.ad.responses.AdResponseDTO;
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

    @GetMapping("/{adId}/likes")
    public ResponseEntity<PagedResponse<AdLikeResponseDTO>> getAdLikes(
        @PathVariable Long adId,
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "0")  int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(adLikeService.getAdLikes(adId, jwt.getClaim("userId"), pageable));
    }

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

    @PostMapping("/complete")
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<Void> completeAdWatch(
            @RequestBody AdLikeRequest req,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Received watch completion for ad ID: {} and session {}",
                req.getAdId(), req.getSessionUUID());
        Long userId = jwt.getClaim("userId");

        if (userId == null) {
            throw new BusinessException("Token inválido");
        }

        adLikeService.markWatchSessionCompleted(req.getSessionUUID(), req.getAdId(), userId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/next")
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<AdForConsumerDTO> getNextAd(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long consumerId = jwt.getClaim("userId");

        return adLikeService.getNextAdForConsumer(consumerId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}/has-liked")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Boolean> hasUserLikedAd(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        
        boolean hasLiked = adLikeService.hasConsumerLikedAd(id, jwt.getClaim("userId"));
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
