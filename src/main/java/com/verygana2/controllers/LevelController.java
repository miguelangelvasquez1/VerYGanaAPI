package com.verygana2.controllers;



import java.util.Arrays;
import java.util.List;

import com.verygana2.services.interfaces.levels.LevelService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.verygana2.dtos.levels.LevelConfigResponse;
import com.verygana2.dtos.levels.LevelProfileResponse;
import com.verygana2.dtos.levels.TransactionLogResponse;
import com.verygana2.models.enums.UserLevel;


import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/levels")
@RequiredArgsConstructor
public class LevelController {

    private final LevelService levelService;

    @GetMapping("/me")
    public ResponseEntity<LevelProfileResponse> getMyProfile(
            @AuthenticationPrincipal Jwt jwt) {

        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(levelService.getProfileResponse(consumerId));
    }

    @GetMapping("/me/history")
    public ResponseEntity<Page<TransactionLogResponse>> getHistory(
            @AuthenticationPrincipal Jwt jwt,
            Pageable pageable) {

        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(levelService.getTransactionHistory(consumerId, pageable));
    }

    /** Tabla de niveles estática para el frontend — sin BD, sin auth */
    @GetMapping("/config")
    public ResponseEntity<List<LevelConfigResponse>> getLevelsConfig() {
        List<LevelConfigResponse> config = Arrays.stream(UserLevel.values())
                .map(l -> new LevelConfigResponse(
                        l,
                        l.getXpMin(),
                        l.getXpMax() == Long.MAX_VALUE ? "∞" : String.valueOf(l.getXpMax()),
                        l.getMultiplier(),
                        l.getReferralKeys(),
                        l.getReferralTickets()
                ))
                .toList();
        return ResponseEntity.ok(config);
    }
}