package com.verygana2.dtos.user.gamedesigner;

import lombok.Data;

@Data
public class GameDesignerProfileResponseDTO {
    private Long id;
    private String name;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String designerCode;
    private String bio;
    private int gamesCreated;
    private int campaignsDesigned;
}
