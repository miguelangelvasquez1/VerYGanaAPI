package com.verygana2.services.referrals;

import com.verygana2.dtos.referral.responses.ReferralInfoDTO;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.services.interfaces.ReferralInfoService;
import com.verygana2.services.interfaces.ReferralQrService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferralInfoServiceImpl implements ReferralInfoService {

    private final ConsumerDetailsRepository consumerDetailsRepository;
    private final ReferralQrService         referralQrService;

    @Value("${app.base-url:http://localhost:3000}")
    private String baseUrl;

    public ReferralInfoServiceImpl(ConsumerDetailsRepository consumerDetailsRepository,
                                   ReferralQrService referralQrService) {
        this.consumerDetailsRepository = consumerDetailsRepository;
        this.referralQrService         = referralQrService;
    }

    @Override
    @Transactional(readOnly = true)
    public ReferralInfoDTO getInfoByEmail(String email) {

        ConsumerDetails details = consumerDetailsRepository
                .findByUserEmail(email)
                .orElseThrow(() -> new RuntimeException(
                        "ConsumerDetails no encontrado para: " + email));

        String referralCode = details.getReferralCode();
        String referralLink = buildLink(referralCode);
        String qrBase64     = referralQrService.generateBase64(referralLink);

        int totalReferrals = consumerDetailsRepository.countByReferredBy(details);

        return new ReferralInfoDTO(
                referralCode,
                referralLink,
                qrBase64,
                totalReferrals,
                details.getName() + " " + details.getLastName(),
                details.getUser().getEmail()
        );
    }

    private String buildLink(String code) {
        return baseUrl + "/register?ref=" + code;
    }
}