package com.verygana2.dtos.user.gamedesigner;

import java.util.UUID;

import lombok.Data;

@Data
public class GameDesignerProfileResponseDTO {
    private UUID publicId;
    private String name;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String designerCode;
    private String bio;
    private int gamesCreated;
    private int campaignsDesigned;
}
