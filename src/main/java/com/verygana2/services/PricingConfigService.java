package com.verygana2.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.verygana2.models.PricingConfig;
import com.verygana2.repositories.PricingConfigRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PricingConfigService {

    private final PricingConfigRepository pricingConfigRepository;

    public List<PricingConfig> getPricingConfigs() {
        return pricingConfigRepository.findAllByActiveTrueOrderByTypeAsc();
    }

    public PricingConfig updatePricingConfig(PricingConfig.PricingType type, Long newValue) {
        PricingConfig current = pricingConfigRepository.findFirstByTypeAndActiveTrueOrderByCreatedAtDesc(type);

        int nextVersion = 1;
        String description = null;

        if (current != null) {
            current.setActive(false);
            pricingConfigRepository.save(current);
            nextVersion = current.getVersion() + 1;
            description = current.getDescription();
        }

        PricingConfig newConfig = PricingConfig.builder()
                .version(nextVersion)
                .type(type)
                .amountInCents(newValue)
                .description(description)
                .active(true)
                .build();

        return pricingConfigRepository.save(newConfig);
    }

    public Long getCurrentValue(PricingConfig.PricingType type) {
        PricingConfig config = pricingConfigRepository.findFirstByTypeAndActiveTrueOrderByCreatedAtDesc(type);
        return config != null ? config.getAmountInCents() : 0L;
    }
}
