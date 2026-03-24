package com.verygana2.services.interfaces;


import com.verygana2.dtos.referral.responses.ReferralInfoDTO;

public interface ReferralInfoService {
    ReferralInfoDTO getInfoByEmail(String email);
}
