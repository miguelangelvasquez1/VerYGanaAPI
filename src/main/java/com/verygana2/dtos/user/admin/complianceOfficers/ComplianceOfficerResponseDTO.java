package com.verygana2.dtos.user.admin.complianceOfficers;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ComplianceOfficerResponseDTO {

    private UUID publicId;
    private Role role;
    private String email;
    private String phoneNumber;
    private UserState userState;
    private ZonedDateTime registeredDate;
    private int failedLoginAttempts;
    private Instant accountLockedAt;

    private String name;
    private String lastName;
    private String badgeNumber;
}
