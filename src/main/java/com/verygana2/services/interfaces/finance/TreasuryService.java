package com.verygana2.services.interfaces.finance;

import java.util.UUID;

import com.verygana2.models.records.TreasurySnapshot;
import com.verygana2.models.userDetails.CommercialDetails;

public interface TreasuryService {
    void distributeDeposit(Long amountCents, CommercialDetails commercial, UUID referenceId);
    void distributeSubscription(Long amountCents, CommercialDetails commercial, UUID referenceId);
    void convertKeysToPayoutPending(Long amountCents, UUID referenceId);
    void moveCashToPayoutPending(Long amountCents, UUID referenceId);
    void retainCommission(Long amountCents, UUID referenceId, String referenceType);
    void registerPayoutSent(Long amountCents, UUID referenceId);
    TreasurySnapshot getSnapshot();
}
