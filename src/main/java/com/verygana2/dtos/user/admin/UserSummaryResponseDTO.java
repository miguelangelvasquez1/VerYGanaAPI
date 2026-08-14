package com.verygana2.dtos.user.admin;

import java.util.UUID;

import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSummaryResponseDTO {
    private UUID publicId;
    private Role role;
    private String email;
    private UserState userState;
}
