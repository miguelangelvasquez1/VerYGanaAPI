package com.verygana2.controllers.admin;

import java.time.ZonedDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.audit.AuditLogSearchResponseDTO;
import com.verygana2.utils.audit.AuditLevel;
import com.verygana2.utils.audit.AuditLogService;

import lombok.RequiredArgsConstructor;

/**
 * Audit logs WARNING/CRITICAL de cualquier categoría EXCEPTO SECURITY (esa ya
 * se maneja en el front vía /admin/security-events). Cubre el resto de
 * eventos relevantes para un admin — ej. PQRS, y lo que se agregue a futuro.
 */
@RestController
@RequestMapping("/admin/audit-logs")
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AuditLogAdminController {

    private final AuditLogService auditLogService;

    /**
     * Devuelve la página de eventos junto con el resumen (conteo por action)
     * calculado con los mismos filtros — no hace falta pegarle a /summary aparte.
     */
    @GetMapping
    public ResponseEntity<AuditLogSearchResponseDTO> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) AuditLevel level,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(auditLogService.search(action, category, level, from, to, pageable));
    }

    @GetMapping("/critical")
    public ResponseEntity<AuditLogSearchResponseDTO> getCritical(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(auditLogService.getCritical(category, from, to, pageable));
    }
}
