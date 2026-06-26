package com.verygana2.dtos.avatar;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateAvatarRequestDTO {

    @NotNull(message = "Avatar id is required")
    private Long avatarId;
}
