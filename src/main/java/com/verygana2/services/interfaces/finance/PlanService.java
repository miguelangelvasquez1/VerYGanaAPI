package com.verygana2.services.interfaces.finance;

import java.util.UUID;

import com.verygana2.dtos.finance.plans.responses.EffectivePlanStateResponseDTO;
import com.verygana2.dtos.finance.plans.responses.PlanPaymentStatusResponseDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;

public interface PlanService {
    
    WompiCheckoutResponseDTO initiatePlanPayment(
            CommercialDetails commercial,
            PlanCode planCode,
            Long amountCents);

    void handleWompiResult(UUID wompiTransactionId);

    PlanPaymentStatusResponseDTO getPaymentStatus(String reference, CommercialDetails commercial);

    EffectivePlanStateResponseDTO getEffectivePlanState(CommercialDetails commercial);

}
