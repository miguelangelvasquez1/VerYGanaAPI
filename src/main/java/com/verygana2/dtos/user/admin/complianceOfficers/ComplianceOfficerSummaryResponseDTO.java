package com.verygana2.dtos.user.admin.complianceOfficers;

import java.util.UUID;

import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ComplianceOfficerSummaryResponseDTO {
    
    private UUID publicId;
    private Role role;
    private String email;
    private UserState userState;

    private String name;
    private String lastName;
}
