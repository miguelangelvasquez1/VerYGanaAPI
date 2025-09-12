package com.VerYGana.services.interfaces;



import com.VerYGana.dtos.Wallet.Requests.BlockBalanceRequest;
import com.VerYGana.dtos.Wallet.Requests.DepositRequest;
import com.VerYGana.dtos.Wallet.Requests.ParticipateRaffleRequest;
import com.VerYGana.dtos.Wallet.Requests.PointsForAdRequest;
import com.VerYGana.dtos.Wallet.Requests.PurchaseRequest;
import com.VerYGana.dtos.Wallet.Requests.RafflePrizeRequest;
import com.VerYGana.dtos.Wallet.Requests.RechargeDataRequest;
import com.VerYGana.dtos.Wallet.Requests.ReferralPointsRequest;
import com.VerYGana.dtos.Wallet.Requests.TransferRequest;
import com.VerYGana.dtos.Wallet.Requests.UnblockBalanceRequest;
import com.VerYGana.dtos.Wallet.Requests.WalletAdvertiserCreateRequest;
import com.VerYGana.dtos.Wallet.Requests.WalletUserCreateRequest;
import com.VerYGana.dtos.Wallet.Requests.WithdrawalRequest;
import com.VerYGana.dtos.Wallet.Responses.BalanceResponse;
import com.VerYGana.dtos.Wallet.Responses.TransactionResponse;
import com.VerYGana.dtos.Wallet.Responses.WalletResponse;

public interface WalletService {
    // Creations
    WalletResponse createWalletForUser(WalletUserCreateRequest walletCreateRequest);
    WalletResponse createWalletForAdvertiser(WalletAdvertiserCreateRequest walletCreateRequest);

    // Get
    WalletResponse getUserWalletByUserId(Long userId);
    WalletResponse getAdvertiserWalletByAdvertiserId(Long advertiserId);

    // add Tpoints (users only)
    TransactionResponse addPointsForWatchingAdAndLike(PointsForAdRequest pointsForAdRequest);
    TransactionResponse addPointsForReferral(ReferralPointsRequest referralPointsRequest);
    TransactionResponse addRafflePrize(RafflePrizeRequest rafflePrizeRequest);

    // Purchases and others
    TransactionResponse doDeposit(DepositRequest depositRequest);
    TransactionResponse doWithdrawal(WithdrawalRequest withdrawalRequest);
    TransactionResponse participateInRaffle (ParticipateRaffleRequest participateRaffleRequest);
    TransactionResponse rechargeData(RechargeDataRequest rechargeDataRequest);
    TransactionResponse transferToUser(TransferRequest transferRequest);
    TransactionResponse doPurchase(PurchaseRequest purchaseRequest);

    // Balance Queries
    BalanceResponse getAvailableBalance(Long ownerId);
    BalanceResponse getBlockedBalance(Long ownerId);

    // Balance Management
    TransactionResponse blockBalance(BlockBalanceRequest blockBalanceRequest);
    TransactionResponse UnblockBalance(UnblockBalanceRequest unblockBalanceRequest);


}
