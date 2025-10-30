package com.verygana2.storage.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * Metadata de un archivo
 */
@Data
@Builder
public class FileMetadata {
    private String publicId;
    private String format;
    private String resourceType;
    private Long bytes;
    private Integer width;
    private Integer height;
    private Double duration;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String url;
    private String secureUrl;
    private Map<String, Object> customMetadata;
}