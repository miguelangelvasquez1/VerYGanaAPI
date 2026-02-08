package com.verygana2.controllers;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.game.GameConfigDefinitionDTO;
import com.verygana2.dtos.game.GameDTO;
import com.verygana2.dtos.game.campaign.AssetUploadPermissionDTO;
import com.verygana2.dtos.game.campaign.CampaignDTO;
import com.verygana2.dtos.game.campaign.CreateCampaignRequestDTO;
import com.verygana2.dtos.game.campaign.GameAssetDefinitionDTO;
import com.verygana2.dtos.game.campaign.PrepareCampaignRequestDTO;
import com.verygana2.dtos.game.campaign.UpdateCampaignRequestDTO;
import com.verygana2.dtos.game.campaign.UpdateCampaignStatusRequest;
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

    @GetMapping
    public ResponseEntity<List<CampaignDTO>> getAdvertiserCampaigns(
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(service.getAdvertiserCampaigns(jwt.getClaim("userId")));
    }

    @PatchMapping("/update-status/{campaignId}")
    public ResponseEntity<Void> updateCampaignStatus(
            @PathVariable Long campaignId,
            @RequestBody UpdateCampaignStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        service.updateCampaignStatus(campaignId, userId, request.getStatus());

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{campaignId}")
    public ResponseEntity<Void> updateCampaign(
            @PathVariable Long campaignId,
            @RequestBody @Valid UpdateCampaignRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        service.updateCampaign(campaignId, userId, request);

        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/campaigns/prepare
     * Paso 1: Validar y obtener URLs de subida
     */
    @PostMapping("/prepare")
    public ResponseEntity<List<AssetUploadPermissionDTO>> prepareCampaign(
            @Valid @RequestBody PrepareCampaignRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) { 
        
       List<AssetUploadPermissionDTO> permissions = 
            service.prepareAssetUploads(
                request.getGameId(),
                jwt.getClaim("userId"),
                request.getAssets()
            );
        
        return ResponseEntity.ok(permissions);
    }

    /**
     * POST /api/campaigns/create
     * Paso 2: Crear campaña con assets ya subidos
     */
    @PostMapping("/create")
    public ResponseEntity<Boolean> createCampaign(  //mandar esto bien y crear los gameconfig en el backend, depsues probar bien el formulario dinamico.
            @RequestParam Long gameId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid CreateCampaignRequestDTO request) {
        log.info(request.toString());
        Long userId = jwt.getClaim("userId");
        service.createCampaignWithAssets(
            gameId, 
            userId, 
            request
        );
        
        return ResponseEntity.ok(true);
    }

    @GetMapping("/games")
    public PagedResponse<GameDTO> getAvailableGames(
            @AuthenticationPrincipal Jwt jwt,
            Pageable pageable) {

        Long userId = jwt.getClaim("userId");
        return service.getAvailableGames(userId, pageable);
    }

    @GetMapping("/config-definitions/{gameId}")
    public ResponseEntity<List<GameConfigDefinitionDTO>> getGameConfigDefinitions(@PathVariable Long gameId) {
        List<GameConfigDefinitionDTO> config = service.getConfigDefinitionByGame(gameId);
        return ResponseEntity.ok(config);
    }
    
    @GetMapping("/{gameId}/asset-definitions")
    public List<GameAssetDefinitionDTO> getAssetDefinitions(
            @PathVariable Long gameId) {
        return service.getAssetsByGame(gameId);
    }
}
