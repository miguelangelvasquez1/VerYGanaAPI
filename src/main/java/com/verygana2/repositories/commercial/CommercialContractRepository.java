package com.verygana2.repositories.commercial;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.enums.commercial.ContractPurpose;
import com.verygana2.models.enums.commercial.ContractStatus;

public interface CommercialContractRepository extends JpaRepository<CommercialContract, Long> {
    Optional<CommercialContract> findByOnboarding_Id(Long onboardingId);
    List<CommercialContract> findByStatus(ContractStatus status);
    List<CommercialContract> findByStatusIn(List<ContractStatus> statuses);
    Optional<CommercialContract> findByEsignatureEnvelopeId(String envelopeId);

    List<CommercialContract> findByPurposeAndStatus(ContractPurpose purpose, ContractStatus status);
    List<CommercialContract> findByPurposeAndStatusIn(ContractPurpose purpose, List<ContractStatus> statuses);

    /** Usado al confirmarse un pago para detectar si financia un cambio de plan pendiente. */
    Optional<CommercialContract> findByInvestment_Id(Long investmentId);
    Optional<CommercialContract> findBySubscription_Id(UUID subscriptionId);

    /**
     * Contratos de recarga "en curso" para un comercial: no rechazados y cuyo
     * depósito (si ya existe) todavía no fue confirmado. Usado para impedir abrir
     * una segunda recarga mientras la anterior no se resuelve.
     */
    @Query("SELECT c FROM CommercialContract c WHERE c.commercial.id = :commercialId "
            + "AND c.purpose = com.verygana2.models.enums.commercial.ContractPurpose.RECHARGE "
            + "AND c.status <> com.verygana2.models.enums.commercial.ContractStatus.REJECTED "
            + "AND (c.investment IS NULL OR c.investment.confirmed = false)")
    List<CommercialContract> findOpenRechargeContracts(@Param("commercialId") Long commercialId);
}
