package com.verygana2.repositories.finance;

import java.util.Optional;
import java.util.UUID;
 
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
 
import com.verygana2.models.finance.TreasuryAccount;
import com.verygana2.models.enums.finance.TreasuryAccountCode;
 
@Repository
public interface TreasuryAccountRepository extends JpaRepository<TreasuryAccount, UUID> {
 
    Optional<TreasuryAccount> findByCode(TreasuryAccountCode code);
 
    boolean existsByCode(TreasuryAccountCode code);
}
