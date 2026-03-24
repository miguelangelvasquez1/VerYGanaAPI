package com.verygana2.dtos.referral.responses;

public record ReferralInfoDTO(
        String referralCode,
        String referralLink,
        String qrCodeBase64,
        int    totalReferrals,
        String consumerName,
        String consumerEmail
) {}
