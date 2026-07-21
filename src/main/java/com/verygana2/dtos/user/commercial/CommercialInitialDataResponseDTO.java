package com.verygana2.dtos.user.commercial;

import com.verygana2.models.enums.commercial.OnboardingStep;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommercialInitialDataResponseDTO {
    
    private String companyName;
    private String nit; 
    private String email;
    private OnboardingStep onboardingStatus;
}
