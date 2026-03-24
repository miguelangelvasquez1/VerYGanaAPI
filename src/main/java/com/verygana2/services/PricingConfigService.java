package com.verygana2.services;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.verygana2.models.PricingConfig;
import com.verygana2.repositories.PricingConfigRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PricingConfigService {

    private final PricingConfigRepository pricingConfigRepository;

    public void createPricingConfig(PricingConfig.PricingType type, BigDecimal value, String currency) {
        // Deactivate old configs of the same type
        PricingConfig oldConfig = pricingConfigRepository.findFirstByTypeAndActiveTrueOrderByCreatedAtDesc(type);
        if (oldConfig != null) {
            oldConfig.setActive(false);
            pricingConfigRepository.save(oldConfig);
        }

        // Antes de guardar uno nuevo activo:
        // pricingConfigRepository.deactivateAllByType(type);

        // Create new active config
        PricingConfig newConfig = PricingConfig.builder()
                .type(type)
                .value(value)
                .currency(currency)
                .active(true)
                .build();
        pricingConfigRepository.save(newConfig);
    }
    
    public BigDecimal getCurrentValue(PricingConfig.PricingType pricingConfig) {
        
        PricingConfig config = pricingConfigRepository.findFirstByTypeAndActiveTrueOrderByCreatedAtDesc(pricingConfig);
        return config != null ? config.getValue() : BigDecimal.ZERO;
    }
}
