package com.verygana2.dtos.user.commercial.onboarding;

import com.verygana2.models.enums.commercial.CommercialRoute;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteClassificationResponseDTO {
    private CommercialRoute route;
    private String routeLabel;
    private String explanation;
    private boolean confirmed;
}
