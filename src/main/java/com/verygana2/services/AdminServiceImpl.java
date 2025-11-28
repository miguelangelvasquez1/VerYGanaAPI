package com.verygana2.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.admin.responses.AdminReportResponse;
import com.verygana2.dtos.wallet.requests.BlockBalanceRequest;
import com.verygana2.dtos.wallet.requests.UnblockBalanceRequest;
import com.verygana2.models.AdminReport;
import com.verygana2.models.Wallet;
import com.verygana2.models.enums.AdminActionType;
import com.verygana2.repositories.AdminReportRepository;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.services.interfaces.AdminService;

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
    public AdminReportResponse blockBalance(BlockBalanceRequest blockBalanceRequest) {
        
        Wallet wallet = walletRepository.findByUserId(blockBalanceRequest.userId()).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for userId: " + blockBalanceRequest.userId(),
                        Wallet.class));

        BigDecimal previousBalance = wallet.getBalance();
        BigDecimal previousBlockedBalance = wallet.getBlockedBalance();

        wallet.blockBalance(blockBalanceRequest.amount());
        walletRepository.save(wallet);
        
        BigDecimal newBalance = wallet.getBalance();
        BigDecimal newBlockedBalance = wallet.getBlockedBalance();

        AdminReport report = AdminReport.createBlockBalanceReport(blockBalanceRequest.userId(), blockBalanceRequest.amount(), blockBalanceRequest.reason(), previousBalance, newBalance, previousBlockedBalance, newBlockedBalance);
        
        adminReportRepository.save(Objects.requireNonNull(report));

        AdminReportResponse response = new AdminReportResponse("Balance blocked", blockBalanceRequest.amount(), newBalance, LocalDateTime.now());

        return response;
    }

    @Override
    public AdminReportResponse unblockBalance(UnblockBalanceRequest unblockBalanceRequest) {
        Wallet wallet = walletRepository.findByUserId(unblockBalanceRequest.userId()).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for userId: " + unblockBalanceRequest.userId(),
                        Wallet.class));

        BigDecimal previousBalance = wallet.getBalance();
        BigDecimal previousBlockedBalance = wallet.getBlockedBalance();

        wallet.unblockBalance(unblockBalanceRequest.amount());
        walletRepository.save(wallet);
        
        BigDecimal newBalance = wallet.getBalance();
        BigDecimal newBlockedBalance = wallet.getBlockedBalance();

        AdminReport report = AdminReport.createUnblockBalanceReport(unblockBalanceRequest.userId(), unblockBalanceRequest.amount(), unblockBalanceRequest.reason(), previousBalance, newBalance, previousBlockedBalance, newBlockedBalance);
        adminReportRepository.save(Objects.requireNonNull(report));

        AdminReportResponse response = new AdminReportResponse("Balance unblocked", unblockBalanceRequest.amount(), newBalance, LocalDateTime.now());

        return response;
    }
    
}
