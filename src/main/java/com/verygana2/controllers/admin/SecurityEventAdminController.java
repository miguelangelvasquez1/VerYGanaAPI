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

import com.verygana2.dtos.security.SecurityEventSearchResponseDTO;
import com.verygana2.security.monitoring.SecurityEventService;
import com.verygana2.utils.audit.AuditLevel;

import lombok.RequiredArgsConstructor;

/**
 * Eventos de seguridad (category=SECURITY en audit_logs): fuerza bruta, token
 * farming, session hijacking, IPs auto-bloqueadas, etc.
 * Generados por SecurityMonitoringService / SecurityAuditService.
 */
@RestController
@RequestMapping("/admin/security-events")
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequiredArgsConstructor
public class SecurityEventAdminController {

    private final SecurityEventService securityEventService;

    /**
     * Devuelve la página de eventos junto con el resumen (conteo por action)
     * calculado con los mismos filtros — no hace falta pegarle a /summary aparte.
     */
    @GetMapping
    public ResponseEntity<SecurityEventSearchResponseDTO> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) AuditLevel level,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(securityEventService.search(action, level, from, to, pageable));
    }

    @GetMapping("/critical")
    public ResponseEntity<SecurityEventSearchResponseDTO> getCritical(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(securityEventService.getCritical(from, to, pageable));
    }
}
