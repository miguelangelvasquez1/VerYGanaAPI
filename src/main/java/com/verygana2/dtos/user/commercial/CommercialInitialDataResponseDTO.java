package com.verygana2.dtos.user.commercial;

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
}
