package com.verygana2.dtos.user.admin;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class SendNotificationsRequestDTO {
    @NotEmpty(message = "publicIds is required")
    private List<UUID> publicIds;
    @NotBlank(message = "message is required")
    private String message;
}
