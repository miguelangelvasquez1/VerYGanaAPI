package com.verygana2.controllers.compliance;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.utils.audit.AuditLevel;
import com.verygana2.utils.audit.AuditLog;
import com.verygana2.utils.audit.AuditLogRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/compliance/audit-logs")
@PreAuthorize("hasRole('ROLE_COMPLIANCE_OFFICER')")
@RequiredArgsConstructor
public class ComplianceAuditController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<Page<AuditLog>> search(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) AuditLevel level,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {

        LocalDateTime start = from != null ? from : LocalDateTime.now().minusDays(30);
        LocalDateTime end = to != null ? to : LocalDateTime.now();

        Page<AuditLog> logs = auditLogRepository.searchAuditLogs(
                userId, action, level, category, success, start, end, pageable);

        return ResponseEntity.ok(logs);
    }

    @GetMapping("/critical")
    public ResponseEntity<Page<AuditLog>> getCritical(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {

        LocalDateTime start = from != null ? from : LocalDateTime.now().minusDays(30);

        Page<AuditLog> logs = auditLogRepository.findRecentByLevel(AuditLevel.CRITICAL, start, pageable);
        return ResponseEntity.ok(logs);
    }
}