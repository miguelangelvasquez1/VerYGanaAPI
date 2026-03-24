package com.verygana2.controllers;


import com.verygana2.dtos.referral.responses.ReferralInfoDTO;
import com.verygana2.dtos.referral.responses.ReferralItemDTO;
import com.verygana2.services.interfaces.ReferralInfoService;
import com.verygana2.services.interfaces.ReferralService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/referrals")
public class ReferralController {

    private final ReferralInfoService referralInfoService;
    private final ReferralService referralService;

    public ReferralController(ReferralInfoService referralInfoService, ReferralService referralService) {
        this.referralInfoService = referralInfoService;
        this.referralService = referralService;
    }

    @GetMapping("/my-code")
    public ResponseEntity<ReferralInfoDTO> getMyReferralCode(
            @AuthenticationPrincipal Jwt jwt) {

        ReferralInfoDTO dto = referralInfoService.getInfoByEmail(jwt.getSubject());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/my-referrals")
    public ResponseEntity<List<ReferralItemDTO>> getMyReferrals(
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(
                referralService.getReferralsByEmail(jwt.getSubject()));
    }
}