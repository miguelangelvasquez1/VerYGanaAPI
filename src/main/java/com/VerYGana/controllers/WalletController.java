// package com.VerYGana.controllers;

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.GetMapping;
// import org.springframework.web.bind.annotation.PathVariable;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestBody;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.RestController;

// import com.VerYGana.DTOS.Wallet.Requests.DepositRequest;
// import com.VerYGana.DTOS.Wallet.Responses.DepositResponse;
// import com.VerYGana.DTOS.Wallet.Responses.WalletResponse;
// import com.VerYGana.models.Wallet;
// import com.VerYGana.models.Enums.WalletOwnerType;
// import com.VerYGana.services.interfaces.WalletService;

// import jakarta.validation.Valid;

// @RestController
// @RequestMapping("/wallets")
// public class WalletController {

//     @Autowired
//     private WalletService walletService;

//     // These methods uses GlobalExceptionHandler

//     @GetMapping("user/{userId}")
//     public ResponseEntity<WalletResponse> getWalletByUserId(@PathVariable Long userId) {
//         Wallet foundWallet = walletService.getUserWalletByUserId(userId);
//         WalletResponse response = mapToResponse(foundWallet);
//         return ResponseEntity.ok(response);
//     }

//     @GetMapping("advertiser/{advertiserId}")
//     public ResponseEntity<WalletResponse> getWalletByAdvertiserId(@PathVariable Long advertiserId) {
//         Wallet foundWallet = walletService.getUserWalletByUserId(advertiserId);
//         WalletResponse response = mapToResponse(foundWallet);
//         return ResponseEntity.ok(response);
//     }

//     // Deposits
//     @PostMapping("user/{userId}/deposit")
//     public ResponseEntity<DepositResponse> doDepositForUser(@PathVariable Long userId,
//             @Valid @RequestBody DepositRequest request) {
//         walletService.doDeposit(userId, WalletOwnerType.USER, request.getAmount());
//         DepositResponse response = new DepositResponse("Deposit completed succesfully", request.getAmount());
//         return ResponseEntity.ok(response);
//     }

//     @PostMapping("advertiser/{advertiserId}/deposit")
//     public ResponseEntity<DepositResponse> doDepositForAdvertiser(@PathVariable Long advertiserId,
//             @Valid @RequestBody DepositRequest request) {
//         walletService.doDeposit(advertiserId, WalletOwnerType.ADVERTISER, request.getAmount());
//         DepositResponse response = new DepositResponse("Deposit completed succesfully", request.getAmount());
//         return ResponseEntity.ok(response);
//     }

//     private WalletResponse mapToResponse(Wallet wallet) {
//         WalletResponse response = new WalletResponse();
//         response.setId(wallet.getId());
//         response.setOwnerId(wallet.getOwnerId());
//         response.setOwnerType(wallet.getOwnerType());
//         response.setBalance(wallet.getBalance());
//         response.setBlockedBalance(wallet.getBlockedBalance());
//         response.setCreatedAt(wallet.getCreatedAt());
//         response.setLastUpdated(wallet.getLastUpdated());
//         return response;
//     }

// }

// /*
//  * void addPointsForWatchingAdAndLike(Long userId, BigDecimal Tpoints, Long
//  * advertiserId);
//  * void addPointsForReferral(Long userId, Long referredUserId, BigDecimal
//  * points);
//  * void addRafflePrize(Long userId, BigDecimal points);
//  * 
//  * // Purchases and others
//  * void doWithdrawal(Long advertiserId, BigDecimal Tpoints);
//  * void participateInRaffle (Long userId, BigDecimal Tpoints);
//  * void rechargeData(Long userId, BigDecimal Tpoints, String phoneNumber);
//  * void transferToUser(Long senderId, BigDecimal Tpoints, Long receiverId);
//  * void doPurchase(Long buyerId, BigDecimal Tpoints, Long sellerId);
//  * 
//  * // Balance Queries
//  * BigDecimal getAvailableBalance(Long ownerId);
//  * BigDecimal getBlockedBalance(Long ownerId);
//  * 
//  * // Balance Management
//  * void blockBalance(Long userId, BigDecimal Tpoints, String reason);
//  * void UnblockBalance(Long userId, BigDecimal Tpoints);
//  */