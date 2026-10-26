package com.verygana2.controllers.admin;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.recognitions.CancelRecognitionRequestDTO;
import com.verygana2.dtos.recognitions.CreateRecognitionRequestDTO;
import com.verygana2.dtos.recognitions.RecognitionCandidateDTO;
import com.verygana2.dtos.recognitions.RecognitionDetailDTO;
import com.verygana2.dtos.recognitions.RecognitionFundDTO;
import com.verygana2.dtos.recognitions.RecognitionSummaryDTO;
import com.verygana2.models.enums.recognitions.RecognitionCriterion;
import com.verygana2.models.enums.recognitions.RecognitionStatus;
import com.verygana2.services.interfaces.recognitions.RecognitionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Reconocimientos de VERYGANA (Contrato B 10.16): el admin elige a un usuario destacado y le
 * regala un producto que VERYGANA le compra a un empresario con el fondo de fortalecimiento.
 */
@RestController
@RequestMapping("/admin/recognitions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RecognitionAdminController {

    private final RecognitionService recognitionService;

    /**
     * GET /admin/recognitions/candidates?criterion=KEYS_EARNED&from=...&to=...
     * Ranking de usuarios activos por el criterio pedido. XP no usa periodo.
     */
    @GetMapping("/candidates")
    public ResponseEntity<PagedResponse<RecognitionCandidateDTO>> candidates(
            @RequestParam RecognitionCriterion criterion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(recognitionService.candidates(criterion, from, to, pageable)));
    }

    /** GET /admin/recognitions/fund — saldo del fondo y lo gastado en reconocimientos. */
    @GetMapping("/fund")
    public ResponseEntity<RecognitionFundDTO> fund() {
        return ResponseEntity.ok(recognitionService.fund());
    }

    /**
     * POST /admin/recognitions — compra y entrega. El header Idempotency-Key es obligatorio:
     * repetir la petición con la misma clave devuelve el reconocimiento ya creado.
     */
    @PostMapping
    public ResponseEntity<RecognitionDetailDTO> create(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateRecognitionRequestDTO request) {
        // Sin clave de origen un reintento de red compraría dos veces con el fondo: 400.
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("El header Idempotency-Key es obligatorio");
        }
        Long adminId = jwt.getClaim("userId");
        return ResponseEntity.ok(recognitionService.create(request, adminId, idempotencyKey));
    }

    /** GET /admin/recognitions?status=&beneficiary=&commercial=&from=&to= */
    @GetMapping
    public ResponseEntity<PagedResponse<RecognitionSummaryDTO>> search(
            @RequestParam(required = false) RecognitionStatus status,
            @RequestParam(required = false) UUID beneficiary,
            @RequestParam(required = false) UUID commercial,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(
                recognitionService.search(status, beneficiary, commercial, from, to, pageable)));
    }

    /** GET /admin/recognitions/{publicId} — reconstrucción completa de la compra. */
    @GetMapping("/{publicId}")
    public ResponseEntity<RecognitionDetailDTO> get(@PathVariable UUID publicId) {
        return ResponseEntity.ok(recognitionService.get(publicId));
    }

    /** POST /admin/recognitions/{publicId}/cancel — devuelve el precio al fondo si aún no se pagó al comercio. */
    @PostMapping("/{publicId}/cancel")
    public ResponseEntity<RecognitionDetailDTO> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID publicId,
            @Valid @RequestBody CancelRecognitionRequestDTO request) {
        Long adminId = jwt.getClaim("userId");
        return ResponseEntity.ok(recognitionService.cancel(publicId, request.cause(), adminId));
    }
}
