package com.verygana2.dtos.branding;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.BrandingRequestStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandingRequestSummaryDTO {

    private Long id;
    private String brandName;
    private String gameName;
    private String commercialName;
    private BrandingRequestStatus status;
    private Long budgetCents;
    private Long estimatedSessions;
    private String adminNotes;
    private String assignedDesignerName;
    private int corporateResourceCount;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
