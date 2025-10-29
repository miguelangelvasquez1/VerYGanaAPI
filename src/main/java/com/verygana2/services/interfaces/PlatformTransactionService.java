package com.verygana2.services.interfaces;

import java.util.List;

import com.verygana2.models.enums.PlatformTransactionType;
import com.verygana2.models.treasury.PlatformTransaction;

public interface PlatformTransactionService {
    PlatformTransaction getByReferenceId(String referenceId);
    List<PlatformTransaction> getByType(PlatformTransactionType type);
}
