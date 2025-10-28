package com.verygana2.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/{id}/like")
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<Boolean> likeAd(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        
        String userAgent = request.getHeader("User-Agent");
        
        adLikeService.processAdLike(
            id, 
            jwt.getClaim("userId"),
            userAgent
        );
        
        return ResponseEntity.ok(true);
    }

    @GetMapping("/{id}/has-liked")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Boolean> hasUserLikedAd(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        
        boolean hasLiked = adLikeService.hasUserLikedAd(id, jwt.getClaim("userId"));
        return ResponseEntity.ok(hasLiked);
    }
}
