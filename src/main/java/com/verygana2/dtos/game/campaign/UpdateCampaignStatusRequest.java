package com.verygana2.dtos.game.campaign;

import com.verygana2.models.enums.CampaignStatus;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCampaignStatusRequest {
    
    @NotNull(message = "Status requerido")
    CampaignStatus status;
}
