package com.VerYGana.services.interfaces;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

import com.VerYGana.dtos.Wallet.Requests.BlockBalanceRequest;
import com.VerYGana.dtos.Wallet.Requests.UnblockBalanceRequest;
import com.VerYGana.dtos.admin.Responses.AdminReportResponse;
import com.VerYGana.models.AdminReport;
import com.VerYGana.models.Enums.AdminActionType;

public interface AdminService {

    Page<AdminReport> findByUserId (Long userId, Pageable pageable);
    Page<AdminReport> findByActionType(AdminActionType actionType, Pageable pageable);
    AdminReportResponse blockBalance(Long userId, BlockBalanceRequest blockBalanceRequest);
    AdminReportResponse unblockBalance(Long userId, UnblockBalanceRequest unblockBalanceRequest);

    
}
