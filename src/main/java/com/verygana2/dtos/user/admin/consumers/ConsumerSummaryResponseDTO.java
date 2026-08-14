package com.verygana2.dtos.user.admin.consumers;

import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.models.enums.Gender;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConsumerSummaryResponseDTO {

    private UUID publicId;
    private Role role;
    private String email;
    private UserState userState;
    
    private String userName;
    private String name;
    private String lastName;
    private String departmentName;
    private String municipalityName;
    private Integer age;
    private Gender gender;
    private ZonedDateTime lastDailyLoginDate;

}
