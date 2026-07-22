package com.verygana2.repositories.commercial;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.enums.commercial.ContractStatus;

public interface CommercialContractRepository extends JpaRepository<CommercialContract, Long> {
    Optional<CommercialContract> findByOnboarding_Id(Long onboardingId);
    List<CommercialContract> findByStatus(ContractStatus status);
    List<CommercialContract> findByStatusIn(List<ContractStatus> statuses);
    Optional<CommercialContract> findByEsignatureEnvelopeId(String envelopeId);
}
