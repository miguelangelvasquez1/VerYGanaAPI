package com.VerYGana.services.interfaces;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

import com.VerYGana.dtos2.admin2.responses2.AdminReportResponse;
import com.VerYGana.dtos2.wallet2.requests2.BlockBalanceRequest;
import com.VerYGana.dtos2.wallet2.requests2.UnblockBalanceRequest;
import com.VerYGana.models.AdminReport;
import com.VerYGana.models.enums2.AdminActionType;

public interface AdminService {

    Page<AdminReport> findByUserId (Long userId, Pageable pageable);
    Page<AdminReport> findByActionType(AdminActionType actionType, Pageable pageable);
    AdminReportResponse blockBalance(Long userId, BlockBalanceRequest blockBalanceRequest);
    AdminReportResponse unblockBalance(Long userId, UnblockBalanceRequest unblockBalanceRequest);

    
}
