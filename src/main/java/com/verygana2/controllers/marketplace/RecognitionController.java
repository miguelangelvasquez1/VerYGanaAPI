package com.verygana2.controllers.marketplace;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.recognitions.ConsumerRecognitionDTO;
import com.verygana2.services.interfaces.recognitions.RecognitionService;

import lombok.RequiredArgsConstructor;

/** Reconocimientos que recibió el consumidor. No aparecen en "mis compras": no los compró él. */
@RestController
@RequestMapping("/recognitions")
@RequiredArgsConstructor
public class RecognitionController {

    private final RecognitionService recognitionService;

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('ROLE_CONSUMER')")
    public ResponseEntity<PagedResponse<ConsumerRecognitionDTO>> received(@AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(PagedResponse.from(recognitionService.receivedBy(consumerId, pageable)));
    }
}
