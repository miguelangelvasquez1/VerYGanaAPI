package com.verygana2.dtos.user.admin.commercials;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.dtos.user.commercial.onboarding.PlanSummaryResponseDTO;
import com.verygana2.models.enums.AnnualRevenueRange;
import com.verygana2.models.enums.DocumentType;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommercialResponseDTO {
    
    private UUID publicId;
    private Role role;
    private String email;
    private String phoneNumber;
    private UserState userState;
    private ZonedDateTime registeredDate;
    private int failedLoginAttempts;
    private Instant accountLockedAt;

    private String companyName;
    private String nit;
    private String ciiuCode;
    private String mercantileRegistration;
    private DocumentType legalRepDocType;
    private String legalRepDocNumber;
    private boolean pep;
    private AnnualRevenueRange annualIncomeRange;
    private String departmentName;
    private String municipalityName;
    private PlanSummaryResponseDTO currentPlan;
    
}
