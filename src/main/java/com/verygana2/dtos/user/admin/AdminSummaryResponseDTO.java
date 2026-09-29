package com.verygana2.dtos.user.admin;

import java.util.UUID;

import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.AccountStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminSummaryResponseDTO {
    
    private UUID publicId;
    private Role role;
    private String email;
    private AccountStatus accountStatus;

    private String adminCode;
}
