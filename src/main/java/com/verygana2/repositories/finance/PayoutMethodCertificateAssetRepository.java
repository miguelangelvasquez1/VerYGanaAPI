package com.verygana2.repositories.finance;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.PayoutMethodCertificateAsset;

@Repository
public interface PayoutMethodCertificateAssetRepository extends JpaRepository<PayoutMethodCertificateAsset, Long> {
    Optional<PayoutMethodCertificateAsset> findByPayoutMethodId(Long payoutMethodId);
}
