package com.VerYGana.services.interfaces;



import com.VerYGana.DTOS.Wallet.Requests.BlockBalanceRequest;
import com.VerYGana.DTOS.Wallet.Requests.DepositRequest;
import com.VerYGana.DTOS.Wallet.Requests.ParticipateRaffleRequest;
import com.VerYGana.DTOS.Wallet.Requests.PointsForAdRequest;
import com.VerYGana.DTOS.Wallet.Requests.PurchaseRequest;
import com.VerYGana.DTOS.Wallet.Requests.RafflePrizeRequest;
import com.VerYGana.DTOS.Wallet.Requests.RechargeDataRequest;
import com.VerYGana.DTOS.Wallet.Requests.ReferralPointsRequest;
import com.VerYGana.DTOS.Wallet.Requests.TransferRequest;
import com.VerYGana.DTOS.Wallet.Requests.UnblockBalanceRequest;
import com.VerYGana.DTOS.Wallet.Requests.WalletAdvertiserCreateRequest;
import com.VerYGana.DTOS.Wallet.Requests.WalletUserCreateRequest;
import com.VerYGana.DTOS.Wallet.Requests.WithdrawalRequest;
import com.VerYGana.DTOS.Wallet.Responses.BalanceResponse;
import com.VerYGana.DTOS.Wallet.Responses.TransactionResponse;
import com.VerYGana.DTOS.Wallet.Responses.WalletResponse;

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
