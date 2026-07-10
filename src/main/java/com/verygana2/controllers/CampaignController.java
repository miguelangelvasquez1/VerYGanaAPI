package com.verygana2.controllers;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.game.campaign.CampaignDTO;
import com.verygana2.dtos.game.campaign.CampaignSummaryDTO;
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
    public ResponseEntity<List<CampaignSummaryDTO>> getCommercialCampaigns(
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(service.getCommercialCampaigns(jwt.getClaim("userId")));
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignDTO> getCampaignDetail(
            @PathVariable Long campaignId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(service.getCampaignDetail(campaignId, userId));
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
}
