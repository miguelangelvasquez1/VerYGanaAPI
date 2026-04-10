package com.verygana2.services.details;

import java.math.BigDecimal;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.user.commercial.CommercialInitialDataResponseDTO;
import com.verygana2.dtos.user.commercial.responses.MonthlyReportResponseDTO;
import com.verygana2.mappers.UserMapper;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.services.interfaces.PurchaseItemService;
import com.verygana2.services.interfaces.TransactionService;
import com.verygana2.services.interfaces.details.CommercialDetailsService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CommercialDetailsServiceImpl implements CommercialDetailsService {
    
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final UserMapper commercialDetailsMapper;
    private final TransactionService transactionService;
    private final PurchaseItemService purchaseItemService;

    @Override
    @Transactional(readOnly = true)
    public CommercialInitialDataResponseDTO getCommercialInitialData(Long commercialId) {
        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }
        CommercialDetails commercialDetails = commercialDetailsRepository.findById(commercialId)
            .orElseThrow(() -> new ObjectNotFoundException("Commercial with id:" + commercialId + " not found", CommercialDetails.class));
        CommercialInitialDataResponseDTO initialData = commercialDetailsMapper.toCommercialInitialDataResponseDTO(commercialDetails);
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
    public void getCommercialStats(Long commercialId) {
        throw new UnsupportedOperationException("Unimplemented method 'getCommercialStats'");
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
        
        BigDecimal monthlyEarningsAmount = transactionService.getCommercialEarningsByMonth(commercialId, year, month);
        BigDecimal monthlySalesAmount = purchaseItemService.getTotalCommercialSalesAmountByMonth(commercialId, year, month);
        BigDecimal monthlyCommissionsAmount = purchaseItemService.getTotalPlatformComissionsByMonth(commercialId, year, month); 

        return MonthlyReportResponseDTO.builder().commercialId(commercialId).month(month).totalSalesAmount(monthlySalesAmount).earnings(monthlyEarningsAmount)
        .totalPlatformCommissionsAmount(monthlyCommissionsAmount).year(year).build();
    }
}
