package com.verygana2.dtos.user.gamedesigner;

import java.time.LocalDate;

import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;

import lombok.Data;

@Data
public class GameDesignerProfileResponseDTO {
    private Long id;
    private String name;
    private String lastName;
    private String email;
    private String phoneNumber;
    private Role role;
    private UserState userState;
    private String designerCode;
    private String bio;
    private int gamesCreated;
    private int campaignsDesigned;
    private boolean canPublishDirectly;
    private LocalDate joinedAt;
}
