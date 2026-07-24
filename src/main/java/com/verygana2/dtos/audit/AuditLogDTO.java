package com.verygana2.dtos.audit;

import java.time.ZonedDateTime;
import java.util.Map;

import com.verygana2.utils.audit.AuditLevel;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuditLogDTO {
    private Long id;
    private Long userId;
    private String username;
    private String userEmail;
    private String action;
    private AuditLevel level;
    private String category;
    private String description;
    private String ipAddress;
    private String userAgent;
    private ZonedDateTime createdAt;
    private Boolean success;
    private Map<String, Object> additionalData;
}
