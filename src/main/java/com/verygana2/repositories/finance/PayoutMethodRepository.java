package com.verygana2.repositories.finance;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.models.finance.PayoutMethod.VerificationStatus;

@Repository
public interface PayoutMethodRepository extends JpaRepository<PayoutMethod, Long> {

    Page<PayoutMethod> findByCommercialId(Long commercialId, Pageable pageable);

    /** Garantiza que el commercial solo accede a sus propios métodos. */
    Optional<PayoutMethod> findByIdAndCommercialId(Long id, Long commercialId);

    /** Para que el admin liste los métodos BANK_TRANSFER pendientes de revisión. */
    Page<PayoutMethod> findByVerificationStatus(VerificationStatus status, Pageable pageable);
}
