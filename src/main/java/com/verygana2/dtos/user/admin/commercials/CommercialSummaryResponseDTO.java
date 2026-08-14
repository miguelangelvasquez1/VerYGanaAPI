package com.verygana2.dtos.user.admin.commercials;

import java.util.UUID;

import com.verygana2.dtos.user.commercial.onboarding.PlanSummaryResponseDTO;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommercialSummaryResponseDTO {
    
    private UUID publicId;
    private Role role;
    private String email;
    private UserState userState;

    private String companyName;
    private String nit;
    private String departmentName;
    private String municipalityName;
    private PlanSummaryResponseDTO currentPlan;

}
