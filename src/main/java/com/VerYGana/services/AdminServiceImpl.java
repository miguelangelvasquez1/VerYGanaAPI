package com.VerYGana.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.VerYGana.dtos.Wallet.Requests.BlockBalanceRequest;
import com.VerYGana.dtos.Wallet.Requests.UnblockBalanceRequest;
import com.VerYGana.dtos.admin.Responses.AdminReportResponse;
import com.VerYGana.models.AdminReport;
import com.VerYGana.models.Wallet;
import com.VerYGana.models.Enums.AdminActionType;
import com.VerYGana.repositories.AdminReportRepository;
import com.VerYGana.repositories.WalletRepository;
import com.VerYGana.services.interfaces.AdminService;

@Service
public class AdminServiceImpl implements AdminService{

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private AdminReportRepository adminReportRepository;

    @Override
    public Page<AdminReport> findByUserId(Long userId, Pageable pageable) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'findByUserId'");
    }

    @Override
    public Page<AdminReport> findByActionType(AdminActionType actionType, Pageable pageable) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'findByActionType'");
    }

    @Override
    public AdminReportResponse blockBalance(Long userId, BlockBalanceRequest blockBalanceRequest) {
        
        Wallet wallet = walletRepository.findByUserId(userId).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for userId: " + userId,
                        Wallet.class));

        BigDecimal previousBalance = wallet.getBalance();
        BigDecimal previousBlockedBalance = wallet.getBlockedBalance();

        wallet.blockBalance(blockBalanceRequest.amount());
        walletRepository.save(wallet);
        
        BigDecimal newBalance = wallet.getBalance();
        BigDecimal newBlockedBalance = wallet.getBlockedBalance();

        AdminReport report = AdminReport.createBlockBalanceReport(userId, blockBalanceRequest.amount(), blockBalanceRequest.reason(), previousBalance, newBalance, previousBlockedBalance, newBlockedBalance);
        
        adminReportRepository.save(report);

        AdminReportResponse response = new AdminReportResponse("Balance blocked", blockBalanceRequest.amount(), newBalance, LocalDateTime.now());

        return response;
    }

    @Override
    public AdminReportResponse unblockBalance(Long userId, UnblockBalanceRequest unblockBalanceRequest) {
        Wallet wallet = walletRepository.findByUserId(userId).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for userId: " + userId,
                        Wallet.class));

        BigDecimal previousBalance = wallet.getBalance();
        BigDecimal previousBlockedBalance = wallet.getBlockedBalance();

        wallet.unblockBalance(unblockBalanceRequest.amount());
        walletRepository.save(wallet);
        
        BigDecimal newBalance = wallet.getBalance();
        BigDecimal newBlockedBalance = wallet.getBlockedBalance();

        AdminReport report = AdminReport.createUnblockBalanceReport(userId, unblockBalanceRequest.amount(), unblockBalanceRequest.reason(), previousBalance, newBalance, previousBlockedBalance, newBlockedBalance);
        adminReportRepository.save(report);

        AdminReportResponse response = new AdminReportResponse("Balance unblocked", unblockBalanceRequest.amount(), newBalance, LocalDateTime.now());

        return response;
    }
    
}
