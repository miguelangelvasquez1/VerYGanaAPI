package com.verygana2.dtos.user.consumer.responses;

import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;

import lombok.Data;

@Data
public class ConsumerProfileResponseDTO {
    private Long id;
    private String name;
    private String lastName;
    private String email;
    private String phoneNumber;
    private Role role;
    private UserState userState;
    private String department;
    private String municipality;
}
