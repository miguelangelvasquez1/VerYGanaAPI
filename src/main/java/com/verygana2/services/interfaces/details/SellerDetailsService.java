package com.verygana2.services.interfaces.details;

import com.verygana2.dtos.seller.responses.MonthlyReportResponseDTO;
import com.verygana2.models.userDetails.SellerDetails;

public interface SellerDetailsService {
    SellerDetails getSellerById (Long sellerId);
    boolean existsSellerById(Long sellerId);
    SellerDetails getSellerByShopName(String shopName);
    void getSellerStats(Long sellerId); // pending
    MonthlyReportResponseDTO getMonthlyReport (Long sellerId, Integer year, Integer month);
}
