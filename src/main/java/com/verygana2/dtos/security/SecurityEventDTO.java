package com.verygana2.dtos.security;

import java.time.ZonedDateTime;
import java.util.Map;

import com.verygana2.utils.audit.AuditLevel;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SecurityEventDTO {
    private Long id;
    private String username;
    private String action;
    private AuditLevel level;
    private String description;
    private String ipAddress;
    private String userAgent;
    private ZonedDateTime createdAt;
    private Boolean success;
    private Map<String, Object> additionalData;
}
