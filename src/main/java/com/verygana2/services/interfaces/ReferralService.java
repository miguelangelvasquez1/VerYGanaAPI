package com.verygana2.services.interfaces;

import com.verygana2.dtos.referral.responses.ReferralItemDTO;
import com.verygana2.models.User;
import com.verygana2.models.userDetails.ConsumerDetails;

import java.util.List;

public interface ReferralService
{

    String generateUniqueCode(int length);

    void prepareNewConsumer(User user, ConsumerDetails details, String referredByCode);

    List<ReferralItemDTO> getReferralsByEmail(String email);
}
