package com.verygana2.controllers.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.pqrs.requests.RespondPqrsRequestDTO;
import com.verygana2.dtos.pqrs.responses.PqrsAdminDetailDTO;
import com.verygana2.models.enums.pqrs.PqrsStatus;
import com.verygana2.models.enums.pqrs.PqrsType;
import com.verygana2.services.interfaces.pqrs.PqrsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/admin/pqrs")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class PqrsAdminController {

    private final PqrsService pqrsService;

    /**
     * GET /api/admin/pqrs — PQRS asignados al admin autenticado (por rotación).
     * No hay vista "de todos" porque cada PQRS le pertenece al admin al que le tocó por turno.
     */
    @GetMapping
    public ResponseEntity<PagedResponse<PqrsAdminDetailDTO>> getAssignedPqrs(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "status", required = false) PqrsStatus status,
            @RequestParam(value = "type", required = false) PqrsType type,
            @PageableDefault(size = 20) Pageable pageable) {

        Long adminUserId = jwt.getClaim("userId");
        return ResponseEntity.ok(pqrsService.getAssignedPqrs(adminUserId, status, type, pageable));
    }

    /**
     * GET /api/admin/pqrs/{id} — detalle de un PQRS asignado al admin autenticado.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PqrsAdminDetailDTO> getPqrsDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminUserId = jwt.getClaim("userId");
        return ResponseEntity.ok(pqrsService.getPqrsDetailForAdmin(id, adminUserId));
    }

    /**
     * PATCH /api/admin/pqrs/{id}/review — marca el PQRS como EN_REVISION.
     */
    @PatchMapping("/{id}/review")
    public ResponseEntity<Void> markUnderReview(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminUserId = jwt.getClaim("userId");
        pqrsService.markUnderReview(id, adminUserId);
        return ResponseEntity.ok().build();
    }

    /**
     * PATCH /api/admin/pqrs/{id}/respond — resuelve el PQRS con una respuesta.
     */
    @PatchMapping("/{id}/respond")
    public ResponseEntity<Void> respondToPqrs(
            @PathVariable Long id,
            @Valid @RequestBody RespondPqrsRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminUserId = jwt.getClaim("userId");
        pqrsService.respondToPqrs(id, dto, adminUserId);
        return ResponseEntity.ok().build();
    }
}
