package com.verygana2.repositories.commercial;

import java.time.ZonedDateTime;
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
    // LEFT JOIN explícito: dereferenciar c.investment.confirmed en el WHERE fuerza un
    // INNER JOIN implícito en HQL y descartaría los contratos sin depósito todavía
    // (investment_id NULL) — justamente los que están en revisión / firmados-sin-pagar,
    // que son los que más importa detectar para no solapar con un cambio de plan.
    @Query("SELECT c FROM CommercialContract c LEFT JOIN c.investment i WHERE c.commercial.id = :commercialId "
            + "AND c.purpose = com.verygana2.models.enums.commercial.ContractPurpose.RECHARGE "
            + "AND c.status NOT IN (com.verygana2.models.enums.commercial.ContractStatus.REJECTED, "
            + "com.verygana2.models.enums.commercial.ContractStatus.CANCELLED) "
            + "AND (i IS NULL OR i.confirmed = false)")
    List<CommercialContract> findOpenRechargeContracts(@Param("commercialId") Long commercialId);

    /**
     * Cuenta contratos de RECHARGE/PLAN_CHANGE generados por un comercial desde
     * {@code since}, sin importar en qué terminaron (firmado, rechazado o cancelado) —
     * cada generación dispara un envío real a firma electrónica y tiene costo, así
     * que se cuentan todos para el rate limit, no solo los que siguen abiertos.
     */
    @Query("SELECT COUNT(c) FROM CommercialContract c WHERE c.commercial.id = :commercialId "
            + "AND c.purpose IN :purposes AND c.generatedAt >= :since")
    long countGeneratedSince(@Param("commercialId") Long commercialId,
            @Param("purposes") List<ContractPurpose> purposes, @Param("since") ZonedDateTime since);
}
