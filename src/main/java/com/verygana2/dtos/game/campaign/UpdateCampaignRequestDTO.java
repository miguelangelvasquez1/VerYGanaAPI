package com.verygana2.dtos.game.campaign;

import java.math.BigDecimal;
import java.util.List;

import com.verygana2.models.enums.TargetGender;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCampaignRequestDTO {
    
    private BigDecimal budget;
    private String targetUrl;
    private List<Long> categoryIds;
    private TargetAudienceDTO targetAudience;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public class TargetAudienceDTO {

        private int minAge;
        private int maxAge;
        private TargetGender gender;
        private List<String> municipalityCodes;
    }
}
