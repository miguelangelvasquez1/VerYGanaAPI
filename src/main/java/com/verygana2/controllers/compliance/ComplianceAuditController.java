package com.verygana2.controllers.compliance;

import java.time.ZonedDateTime;

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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {

        ZonedDateTime start = from != null ? from : ZonedDateTime.now().minusDays(30);
        ZonedDateTime end = to != null ? to : ZonedDateTime.now();

        Page<AuditLog> logs = auditLogRepository.searchAuditLogs(
                userId, action, level, category, success, start, end, pageable);

        return ResponseEntity.ok(logs);
    }

    @GetMapping("/critical")
    public ResponseEntity<Page<AuditLog>> getCritical(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {

        ZonedDateTime start = from != null ? from : ZonedDateTime.now().minusDays(30);

        Page<AuditLog> logs = auditLogRepository.findRecentByLevel(AuditLevel.CRITICAL, start, pageable);
        return ResponseEntity.ok(logs);
    }
}