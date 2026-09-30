package com.verygana2.dtos.audit;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import com.verygana2.utils.audit.AuditLevel;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuditLogDTO {
    private Long id;
    private UUID userPublicId;
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
    private String entityType;
    /** Id de la entidad relacionada cuando NO es un usuario. */
    private Long entityId;
    /** publicId de la entidad relacionada cuando es un usuario (el id interno nunca se expone). */
    private UUID entityPublicId;
    private Map<String, Object> additionalData;
}
