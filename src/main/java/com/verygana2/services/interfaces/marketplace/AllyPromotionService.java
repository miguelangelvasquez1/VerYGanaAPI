package com.verygana2.services.interfaces.marketplace;

import java.util.List;

import com.verygana2.dtos.product.responses.AllyCommercialResponseDTO;
import com.verygana2.dtos.product.responses.AllyPromotionResponseDTO;

public interface AllyPromotionService {

    /**
     * Alterna la promoción de un producto de un comercial aliado: si ya está
     * promocionado por este Premium lo quita, si no lo agrega (validando elegibilidad
     * y el límite máximo).
     */
    void toggleAllyPromotion(Long premiumCommercialId, Long productId);

    List<AllyPromotionResponseDTO> getMyPromotions(Long premiumCommercialId);

    /** Aliados de un Premium: comerciales distintos dueños de los productos que promociona. */
    List<AllyCommercialResponseDTO> getMyAllies(Long premiumCommercialId);

    /** Aliados de un Básico/Estándar: Premiums distintos que promocionan alguno de sus productos. */
    List<AllyCommercialResponseDTO> getMyPromoters(Long commercialId);
}
