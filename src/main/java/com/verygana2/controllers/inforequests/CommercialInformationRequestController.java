package com.verygana2.controllers.inforequests;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.inforequests.InformationRequestDTOs.RequestView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.RespondRequest;
import com.verygana2.services.interfaces.inforequests.InformationRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Requerimientos de información que VERYGANA le hizo al empresario, y sus respuestas. */
@RestController
@RequestMapping("/commercials/information-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COMMERCIAL')")
public class CommercialInformationRequestController {

    private final InformationRequestService requestService;

    @GetMapping
    public ResponseEntity<List<RequestView>> list(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(requestService.listForCommercial(userId));
    }

    @GetMapping("/{requestPublicId}")
    public ResponseEntity<RequestView> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestPublicId) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(requestService.getForCommercial(userId, requestPublicId));
    }

    /** Aclaración y/o documentos del expediente. Responder no cierra: compliance revisa. */
    @PostMapping("/{requestPublicId}/responses")
    public ResponseEntity<RequestView> respond(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestPublicId,
            @Valid @RequestBody RespondRequest request) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(requestService.respond(userId, requestPublicId, request));
    }
}
