package com.verygana2.dtos.user.commercial.onboarding;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.commercial.CommercialDocumentStatus;
import com.verygana2.models.enums.commercial.CommercialDocumentType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommercialDocumentResponseDTO {
    private Long id;
    private CommercialDocumentType documentType;
    private String originalFileName;
    private Long sizeBytes;
    private CommercialDocumentStatus status;
    private ZonedDateTime uploadedAt;
    private String downloadUrl;
}
