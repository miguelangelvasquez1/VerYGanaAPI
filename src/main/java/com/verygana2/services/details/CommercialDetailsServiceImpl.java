package com.verygana2.services.details;

import java.math.BigDecimal;

import org.hibernate.ObjectNotFoundException;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.product.responses.CommercialProfileResponseDTO;
import com.verygana2.dtos.user.commercial.CommercialInitialDataResponseDTO;
import com.verygana2.dtos.user.commercial.responses.MonthlyReportResponseDTO;
import com.verygana2.mappers.UserMapper;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.services.interfaces.details.CommercialDetailsService;
import com.verygana2.services.interfaces.finance.PayoutService;
import com.verygana2.services.interfaces.marketplace.ProductCategoryService;
import com.verygana2.services.interfaces.marketplace.ProductReviewService;
import com.verygana2.services.interfaces.marketplace.ProductService;
import com.verygana2.services.interfaces.marketplace.PurchaseItemService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CommercialDetailsServiceImpl implements CommercialDetailsService {

    private final CommercialDetailsRepository commercialDetailsRepository;
    private final UserMapper userMapper;
    private final PurchaseItemService purchaseItemService;
    private final ProductReviewService productReviewService;
    private final ProductCategoryService productCategoryService;
    private final PayoutService payoutService;

    // @Lazy rompe el ciclo: CommercialDetailsService ↔ ProductService
    @Lazy
    private final ProductService productService;

    @Override
    @Transactional(readOnly = true)
    public CommercialInitialDataResponseDTO getCommercialInitialData(Long commercialId) {
        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }
        CommercialDetails commercialDetails = commercialDetailsRepository.findById(commercialId)
            .orElseThrow(() -> new ObjectNotFoundException("Commercial with id:" + commercialId + " not found", CommercialDetails.class));
        CommercialInitialDataResponseDTO initialData = userMapper.toCommercialInitialDataResponseDTO(commercialDetails);
        return initialData;
    }

    @Override
    public CommercialDetails getCommercialById(Long commercialId) {
        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }
        return commercialDetailsRepository.findByUser_Id(commercialId).orElseThrow(() -> new ObjectNotFoundException("The commercial with id: " + commercialId + " not found ", CommercialDetails.class));
    }

    @Override
    public CommercialDetails getCommercialByCompanyName(String companyName) {
        if (companyName.isBlank()) {
            throw new IllegalArgumentException("The company name cannot be empty");
        }
        return commercialDetailsRepository.findByCompanyName(companyName).orElseThrow(() -> new ObjectNotFoundException("The commercial with company name: " + companyName + " not found", CommercialDetails.class));
    }

    @Override
    public boolean existsCommercialById(Long commercialId) {
        if (commercialId == null || commercialId <= 0) {
            return false;
        }
        return commercialDetailsRepository.existsById(commercialId);
    }

    @Override
    public MonthlyReportResponseDTO getMonthlyReport(Long commercialId, Integer year, Integer month) {
        
        BigDecimal monthlyEarningsAmount = payoutService.getCommercialEarningsForPeriod(commercialId, year, month);
        BigDecimal monthlySalesAmount = purchaseItemService.getTotalCommercialSalesAmountByMonth(commercialId, year, month);
        BigDecimal monthlyCommissionsAmount = purchaseItemService.getTotalPlatformComissionsByMonth(commercialId, year, month); 

        return MonthlyReportResponseDTO.builder().commercialId(commercialId).month(month).totalSalesAmount(monthlySalesAmount).earnings(monthlyEarningsAmount)
        .totalPlatformCommissionsAmount(monthlyCommissionsAmount).year(year).build();
    }

    @Override
    public CommercialProfileResponseDTO getCommercialProfile(Long commercialId) {

        CommercialDetails commercial = getCommercialById(commercialId);
        CommercialProfileResponseDTO commercialProfile = userMapper.toCommercialProfileResponseDTO(commercial);
        commercialProfile.setAverageRate(productReviewService.getCommercialAvgRating(commercialId));
        commercialProfile.setReviewCount(productReviewService.getCommercialReviewCount(commercialId));
        commercialProfile.setProductCategories(productCategoryService.getCommercialProductCategories(commercialId));
        commercialProfile.setTotalActiveProducts(productService.getTotalCommercialProducts(commercialId, ProductStatus.ACTIVE));
        commercialProfile.setActiveProducts(productService.getCommercialProducts(commercialId, 0));
        
        return commercialProfile;

        
    }
}
