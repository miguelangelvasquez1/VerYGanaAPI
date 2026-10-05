package com.verygana2.mappers.finance;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.prosperity.ProsperityMovementResponseDTO;
import com.verygana2.dtos.prosperity.ProsperitySummaryResponseDTO;
import com.verygana2.dtos.prosperity.ProsperityThresholdResponseDTO;
import com.verygana2.models.finance.prosperity.ProsperityAccount;
import com.verygana2.models.finance.prosperity.ProsperityLedgerEntry;
import com.verygana2.models.finance.prosperity.ProsperityThreshold;

@Mapper(componentModel = "spring")
public interface ProsperityMapper {

    /** {@code reversedAt}: fecha del asiento THRESHOLD_REVERSAL del Umbral, o null si sigue vigente. */
    @Mapping(target = "id", source = "threshold.id")
    @Mapping(target = "investmentId", source = "threshold.investment.id")
    @Mapping(target = "investmentNetCents", source = "threshold.investmentNetCents")
    @Mapping(target = "multiplier", source = "threshold.multiplier")
    @Mapping(target = "generatedCents", source = "threshold.generatedCents")
    @Mapping(target = "planVersion", source = "threshold.planVersion")
    @Mapping(target = "validatedAt", source = "threshold.validatedAt")
    @Mapping(target = "reversed", expression = "java(reversedAt != null)")
    @Mapping(target = "reversedAt", source = "reversedAt")
    ProsperityThresholdResponseDTO toThresholdResponseDTO(ProsperityThreshold threshold, ZonedDateTime reversedAt);

    @Mapping(target = "thresholdId", source = "threshold.id")
    @Mapping(target = "relatedEntryId", source = "relatedEntry.id")
    ProsperityMovementResponseDTO toMovementResponseDTO(ProsperityLedgerEntry entry);

    /**
     * Resumen de una cuenta existente. {@code status} (ACTIVE/FROZEN) y el texto legal
     * no viven en la cuenta: los decide ProsperityServiceImpl según el plan actual.
     */
    @Mapping(target = "commercialPublicId", source = "commercialPublicId")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "balanceCents", source = "account.balanceCents")
    @Mapping(target = "accumulatedThresholdCents", source = "account.accumulatedThresholdCents")
    @Mapping(target = "totalAbsorbedCents", source = "account.totalAbsorbedCents")
    @Mapping(target = "totalReintegratedCents", source = "account.totalReintegratedCents")
    @Mapping(target = "thresholds", source = "thresholds")
    @Mapping(target = "disclaimer", source = "disclaimer")
    ProsperitySummaryResponseDTO toSummaryResponseDTO(ProsperityAccount account, UUID commercialPublicId, String status,
            List<ProsperityThresholdResponseDTO> thresholds, String disclaimer);

    /** Resumen de un comercial que todavía no tiene cuenta de prosperidad: todo en cero. */
    default ProsperitySummaryResponseDTO toEmptySummaryResponseDTO(UUID commercialPublicId, String status,
            String disclaimer) {
        return new ProsperitySummaryResponseDTO(commercialPublicId, status, 0, 0, 0, 0, List.of(), disclaimer);
    }
}
