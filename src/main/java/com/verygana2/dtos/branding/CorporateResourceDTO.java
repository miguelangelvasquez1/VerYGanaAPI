package com.verygana2.dtos.branding;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.AssetStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorporateResourceDTO {

    private Long id;
    private String originalFileName;
    private String contentType;
    private Long sizeBytes;
    private AssetStatus status;
    private String temporalUrl;
    private ZonedDateTime createdAt;
}
