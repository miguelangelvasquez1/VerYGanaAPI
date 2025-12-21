package com.verygana2.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
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

import com.verygana2.dtos.game.campaign.AssetUploadPermissionDTO;
import com.verygana2.dtos.game.campaign.CreateCampaignRequestDTO;
import com.verygana2.dtos.game.campaign.GameAssetDefinitionDTO;
import com.verygana2.services.interfaces.CampaignService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/campaigns")
@RequiredArgsConstructor
@Validated
@Slf4j
public class CampaignController {

    private final CampaignService service;

    /**
     * POST /api/campaigns/prepare
     * Paso 1: Validar y obtener URLs de subida
     */
    @PostMapping("/prepare")
    public ResponseEntity<Map<Long, AssetUploadPermissionDTO>> prepareCampaign(
            @Valid @RequestBody CreateCampaignRequestDTO request) {
        
        Map<Long, AssetUploadPermissionDTO> permissions = 
            service.prepareAssetUploads(
                request.getGameId(), 
                request.getAssets()
            );
        
        return ResponseEntity.ok(permissions);
    }

    /**
     * POST /api/campaigns/create
     * Paso 2: Crear campaña con assets ya subidos
     */
    @PostMapping("/create")
    public ResponseEntity<Boolean> createCampaign(
            @RequestParam Long gameId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<Long, String> uploadedAssets) {
        
        Long userId = jwt.getClaim("userId");
        service.createCampaignWithAssets(
            gameId, 
            userId, 
            uploadedAssets
        );
        
        return ResponseEntity.ok(true);
    }
    
    @GetMapping("/{gameId}/asset-definitions")
    public List<GameAssetDefinitionDTO> getAssetDefinitions(
            @PathVariable Long gameId
    ) {
        return service.getAssetsByGame(gameId);
    }
}
