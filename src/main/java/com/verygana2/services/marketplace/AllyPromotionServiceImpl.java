package com.verygana2.services.marketplace;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.product.responses.AllyCommercialResponseDTO;
import com.verygana2.dtos.product.responses.AllyPromotionResponseDTO;
import com.verygana2.exceptions.AllyPromotionException;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.finance.plans.RequirePlanCapability;
import com.verygana2.models.marketplace.AllyProductPromotion;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.marketplace.AllyProductPromotionRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.services.interfaces.marketplace.AllyPromotionService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AllyPromotionServiceImpl implements AllyPromotionService {

    /** Máximo de productos de aliados que un Premium puede promocionar a la vez (mismo tope que game rewards). */
    private static final int MAX_ALLY_PROMOTIONS = 3;

    private final AllyProductPromotionRepository allyProductPromotionRepository;
    private final ProductRepository productRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;

    @Override
    @RequirePlanCapability(value = RequirePlanCapability.Capability.CAN_PROMOTE_ALLY_PRODUCTS,
            commercialIdParam = "premiumCommercialId", requiresBudget = true)
    public void toggleAllyPromotion(Long premiumCommercialId, Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));

        var existing = allyProductPromotionRepository
                .findByPremiumCommercial_IdAndProduct_Id(premiumCommercialId, productId);

        if (existing.isPresent()) {
            allyProductPromotionRepository.delete(existing.get());
            return;
        }

        CommercialDetails allyCommercial = product.getCommercial();

        if (!isEligibleAlly(allyCommercial)) {
            throw new InvalidRequestException(
                    "El empresario dueño del producto no es elegible como aliado (debe tener plan Básico o Estándar activo)");
        }

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new InvalidStatusException("Solo se pueden promocionar productos activos");
        }

        if (allyProductPromotionRepository.countByPremiumCommercial_Id(premiumCommercialId) >= MAX_ALLY_PROMOTIONS) {
            throw new AllyPromotionException(
                    "Ya tienes " + MAX_ALLY_PROMOTIONS + " productos de aliados promocionados");
        }

        CommercialDetails premiumCommercial = commercialDetailsRepository.findById(premiumCommercialId)
                .orElseThrow(() -> new EntityNotFoundException("Commercial not found: " + premiumCommercialId));

        allyProductPromotionRepository.save(AllyProductPromotion.builder()
                .premiumCommercial(premiumCommercial)
                .product(product)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllyPromotionResponseDTO> getMyPromotions(Long premiumCommercialId) {
        return allyProductPromotionRepository.findByPremiumCommercialIdOrderByCreatedAtDesc(premiumCommercialId)
                .stream()
                .map(promo -> {
                    Product p = promo.getProduct();
                    return AllyPromotionResponseDTO.builder()
                            .productId(p.getId())
                            .productName(p.getName())
                            .productImageUrl(p.getImageUrl())
                            .allyCommercialId(p.getCommercial().getId())
                            .allyCommercialName(p.getCommercial().getCompanyName())
                            .priceCents(p.getPriceCents())
                            .promotedAt(promo.getCreatedAt())
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllyCommercialResponseDTO> getMyAllies(Long premiumCommercialId) {
        return allyProductPromotionRepository.findDistinctAlliesOfPremium(premiumCommercialId)
                .stream()
                .map(this::toAllyCommercialResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllyCommercialResponseDTO> getMyPromoters(Long commercialId) {
        return allyProductPromotionRepository.findDistinctPromotersOfCommercial(commercialId)
                .stream()
                .map(this::toAllyCommercialResponse)
                .toList();
    }

    private AllyCommercialResponseDTO toAllyCommercialResponse(CommercialDetails commercial) {
        return AllyCommercialResponseDTO.builder()
                .commercialId(commercial.getId())
                .companyName(commercial.getCompanyName())
                .planCode(commercial.getCurrentPlan() != null ? commercial.getCurrentPlan().getCode().name() : null)
                .build();
    }

    private boolean isEligibleAlly(CommercialDetails commercial) {
        Plan plan = commercial.getCurrentPlan();
        return plan != null && (plan.getCode() == PlanCode.BASIC || plan.getCode() == PlanCode.STANDARD);
    }
}
