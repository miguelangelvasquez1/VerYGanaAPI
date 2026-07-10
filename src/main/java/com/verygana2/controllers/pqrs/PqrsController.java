package com.verygana2.controllers.pqrs;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.pqrs.requests.CreatePqrsRequestDTO;
import com.verygana2.dtos.pqrs.responses.PqrsResponseDTO;
import com.verygana2.services.interfaces.pqrs.PqrsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/pqrs")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class PqrsController {

    private final PqrsService pqrsService;

    /**
     * POST /pqrs — cualquier usuario autenticado radica una petición, queja, reclamo o sugerencia.
     */
    @PostMapping
    public ResponseEntity<PqrsResponseDTO> createPqrs(
            @Valid @RequestBody CreatePqrsRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        PqrsResponseDTO response = pqrsService.createPqrs(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /pqrs/mine — PQRS radicados (based) por el usuario autenticado.
     */
    @GetMapping("/mine")
    public ResponseEntity<PagedResponse<PqrsResponseDTO>> getMyPqrs(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable) {

        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(pqrsService.getMyPqrs(userId, pageable));
    }

    /**
     * GET /pqrs/{id} — detalle de un PQRS propio.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PqrsResponseDTO> getMyPqrsDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(pqrsService.getMyPqrsDetail(id, userId));
    }
}
