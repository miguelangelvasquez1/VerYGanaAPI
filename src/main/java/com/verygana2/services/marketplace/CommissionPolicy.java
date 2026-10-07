package com.verygana2.services.marketplace;

import org.springframework.stereotype.Component;

import com.verygana2.exceptions.BusinessException;
import com.verygana2.models.enums.CommercialActivityType;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;

/**
 * Porcentaje de comisión que aplica a las ventas de un comercial. Vive aparte de
 * PurchaseServiceImpl porque también lo necesita quien le muestra al empresario las
 * condiciones de una promoción antes de que la acepte (Contrato B 6.22.8).
 */
@Component
public class CommissionPolicy {

    /**
     * Reglas (Contrato Tipo B, cláusula 11.2 / MP-02):
     * - STANDARD (Empresa Tipo B) → depende de su Vocación Empresarial Principal:
     *     PRODUCTS → plan.saleCommissionPct (10% por defecto)
     *     SERVICES → plan.servicesCommissionPct (15% por defecto)
     * - BASIC y PREMIUM → sin cambios, comisión plana de plan.saleCommissionPct
     *   (la vocación aún no tiene efecto tarifario fuera de Tipo B).
     *
     * Los porcentajes NUNCA se hardcodean aquí: viven en Plan (mismo campo
     * directo admin-editable que saleCommissionPct) para poder ajustarse desde
     * un futuro endpoint de administración sin tocar código.
     */
    public int commissionPctFor(CommercialDetails commercial) {
        Plan plan = commercial.getCurrentPlan();
        if (plan.getCode() != PlanCode.STANDARD) {
            return plan.getSaleCommissionPct();
        }

        CommercialActivityType vocacion = commercial.getCommercialActivityType();
        if (vocacion == null) {
            throw new BusinessException(
                    "El comercial Tipo B (STANDARD) no tiene Vocación Empresarial Principal asignada");
        }

        return vocacion == CommercialActivityType.SERVICES
                ? plan.getServicesCommissionPct()
                : plan.getSaleCommissionPct();
    }
}
