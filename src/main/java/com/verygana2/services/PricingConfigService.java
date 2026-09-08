package com.verygana2.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.verygana2.models.PricingConfig;
import com.verygana2.repositories.PricingConfigRepository;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PricingConfigService {

    private final PricingConfigRepository pricingConfigRepository;

    public List<PricingConfig> getPricingConfigs() {
        return pricingConfigRepository.findAllByActiveTrueOrderByTypeAsc();
    }

    public PricingConfig updatePricingConfig(PricingConfig.PricingType type, Long newValue) {
        if (newValue == null || newValue <= 0) {
            throw new ValidationException("El valor debe ser mayor a 0");
        }
        // El anunciante solo puede fijar pricePerLike en múltiplos de 10 (lo exige
        // CreateAdRequestDTO). Si el coste por segundo no lo es, el mínimo derivado
        // (segundos facturables × coste) necesita redondeo hacia arriba y deja de
        // coincidir con el cálculo directo sin redondear. Forzándolo a múltiplo de
        // 10 el redondeo queda garantizado como no-op y no hay ambigüedad.
        if (type == PricingConfig.PricingType.AD_COST_PER_SECOND_CENTS && newValue % 10 != 0) {
            throw new ValidationException("AD_COST_PER_SECOND_CENTS debe ser múltiplo de 10");
        }

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
