package com.verygana2.storage.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Transformaciones de imagen/video
 */
@Data
@Builder
public class Transformation {
    private Integer width;
    private Integer height;
    private String crop;
    private String gravity;
    private Integer quality;
    private String format;
}