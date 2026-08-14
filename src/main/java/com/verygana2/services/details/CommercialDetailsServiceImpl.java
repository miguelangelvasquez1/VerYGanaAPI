package com.verygana2.services.details;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.product.responses.CommercialProfileResponseDTO;
import com.verygana2.dtos.user.admin.commercials.CommercialResponseDTO;
import com.verygana2.dtos.user.admin.commercials.CommercialSummaryResponseDTO;
import com.verygana2.dtos.user.commercial.CommercialInitialDataResponseDTO;
import com.verygana2.dtos.user.commercial.responses.DailySaleResponseDTO;
import com.verygana2.dtos.user.commercial.responses.PayoutReportResponseDTO;
import com.verygana2.dtos.user.commercial.responses.SalesReportResponseDTO;
import com.verygana2.mappers.UserMapper;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.services.interfaces.details.CommercialDetailsService;
import com.verygana2.services.interfaces.finance.PayoutService;
import com.verygana2.services.interfaces.marketplace.ProductCategoryService;
import com.verygana2.services.interfaces.marketplace.ProductReviewService;
import com.verygana2.services.interfaces.marketplace.ProductService;
import com.verygana2.services.interfaces.marketplace.PurchaseItemService;

import jakarta.persistence.EntityNotFoundException;
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
    public PayoutReportResponseDTO getPayoutReport(Long commercialId, Integer year, Integer month) {

        ZonedDateTime startDate = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime endDate = startDate.plusMonths(1);
        
        BigDecimal monthlyEarningsAmount = payoutService.getCommercialEarningsForDateRange(commercialId, startDate, endDate);
        BigDecimal commissionsAmount = purchaseItemService.getTotalPlatformComissionsByDateRange(commercialId, startDate, endDate);

        return PayoutReportResponseDTO.builder().commercialId(commercialId).month(month).earnings(monthlyEarningsAmount)
        .totalPlatformCommissionsAmount(commissionsAmount).year(year).build();
    }

    @Override
    @Transactional(readOnly = true)
    public PayoutReportResponseDTO[] getPayoutReports(Long commercialId, Integer year) {
        PayoutReportResponseDTO[] reports = new PayoutReportResponseDTO[12];
        for (int month = 1; month <= 12; month++) {
            reports[month - 1] = getPayoutReport(commercialId, year, month);
        }
        return reports;
    }

    @Override
    @Transactional(readOnly = true)
    public SalesReportResponseDTO[] getSalesReports(Long commercialId, Integer year) {
        ZoneId zone = ZoneId.of("America/Bogota");
        SalesReportResponseDTO[] reports = new SalesReportResponseDTO[12];
        for (int month = 1; month <= 12; month++) {
            ZonedDateTime startDate = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, zone);
            ZonedDateTime endDate = startDate.plusMonths(1);
            SalesReportResponseDTO monthlyReport = getSalesReport(commercialId, startDate, endDate);
            monthlyReport.setMonth(month);
            monthlyReport.setYear(year);
            reports[month - 1] = monthlyReport;
        }
        return reports;
    }

    @Override
    @Transactional(readOnly = true)
    public SalesReportResponseDTO getSalesReport(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate) {

        BigDecimal salesAmount = purchaseItemService.getTotalCommercialSalesAmountByDateRange(commercialId, startDate, endDate);
        Integer salesCount = purchaseItemService.getTotalCommercialSalesByDateRange(commercialId, startDate, endDate);
        BigDecimal commissionsAmount = purchaseItemService.getTotalPlatformComissionsByDateRange(commercialId, startDate, endDate);
        var topSellingProducts = purchaseItemService
                .getTopSellingProductsByDateRangePage(commercialId, startDate, endDate, PageRequest.of(0, 5))
                .getData();

        return SalesReportResponseDTO.builder()
                .commercialId(commercialId)
                .startDate(startDate)
                .endDate(endDate)
                .totalSalesAmount(salesAmount)
                .totalSalesCount(salesCount)
                .totalPlatformCommissionsAmount(commissionsAmount)
                .topSellingProducts(topSellingProducts)
                .build();
    }

    @Override
    public Integer getSalesCount(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate) {
        getCommercialById(commercialId);
        return purchaseItemService.getTotalCommercialSalesByDateRange(commercialId, startDate, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<DailySaleResponseDTO> getDailySales(
            Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable) {
        return purchaseItemService.getDailySalesPage(commercialId, startDate, endDate, pageable);
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

    @Override
    public PagedResponse<CommercialSummaryResponseDTO> getCommercials(String search, UserState userState,
            PlanCode currentPlan, Pageable pageable) {

        return PagedResponse.from(commercialDetailsRepository.findCommercials(search, userState, currentPlan, pageable).map(userMapper::toCommercialSummaryResponseDTO));
    }

    @Override
    public CommercialResponseDTO getCommercial(UUID publicId) {
        return userMapper.toCommercialResponseDTO(commercialDetailsRepository.findByPublicId(publicId).orElseThrow(() -> new EntityNotFoundException("Commercial with public id: " + publicId + " not found")));
    }
}
