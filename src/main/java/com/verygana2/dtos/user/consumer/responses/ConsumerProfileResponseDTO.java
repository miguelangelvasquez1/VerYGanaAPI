package com.verygana2.dtos.user.consumer.responses;

import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.AccountStatus;

import lombok.Data;

@Data
public class ConsumerProfileResponseDTO {
    private Long id;
    private String name;
    private String lastName;
    private String email;
    private String phoneNumber;
    private Role role;
    private AccountStatus accountStatus;
    private String department;
    private String municipalityName;
}
