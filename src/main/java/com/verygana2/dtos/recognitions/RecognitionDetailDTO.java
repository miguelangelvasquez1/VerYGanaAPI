package com.verygana2.dtos.recognitions;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import com.verygana2.models.enums.marketplace.ComputableSaleStatus;
import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.enums.marketplace.SaleComponentStatus;
import com.verygana2.models.enums.marketplace.SaleComponentType;
import com.verygana2.models.enums.marketplace.SalePayerType;
import com.verygana2.models.enums.recognitions.RecognitionCriterion;
import com.verygana2.models.enums.recognitions.RecognitionStatus;

/**
 * Reconstrucción completa de un reconocimiento para el admin (Contrato B 10.16 H): quién lo
 * otorgó y por qué, compradora, proveedor y beneficiario, recurso del fondo, componentes del
 * precio, absorción de Prosperidad, comisión, entrega y payout de cada ítem.
 */
public record RecognitionDetailDTO(
        UUID publicId,
        RecognitionStatus status,
        RecognitionCriterion criterion,
        Long metricValue,
        ZonedDateTime periodFrom,
        ZonedDateTime periodTo,
        String reason,
        UUID createdByAdminPublicId,
        ZonedDateTime createdAt,
        ZonedDateTime deliveredAt,
        ZonedDateTime closedAt,
        String cancelCause,
        String buyer,
        UUID beneficiaryPublicId,
        String beneficiaryName,
        UUID commercialPublicId,
        String commercialName,
        Long purchaseId,
        UUID fundingRef,
        long totalCents,
        long prosperityAbsorbedCents,
        long commissionCents,
        long netToCommercialCents,
        List<Item> items) {

    public record Item(
            Long itemId,
            String productName,
            PurchaseItemStatus status,
            ComputableSaleStatus computableStatus,
            ZonedDateTime recognizedAt,
            long subtotalCents,
            long prosperityAbsorbedCents,
            Long commissionBaseCents,
            int commissionPctApplied,
            long commissionCents,
            long commissionVatCents,
            long commissionNetOfVatCents,
            long netToCommercialCents,
            ZonedDateTime deliveredAt,
            ZonedDateTime claimedAt,
            ZonedDateTime claimExpiresAt,
            boolean paidOut,
            List<Component> components) {
    }

    public record Component(
            SaleComponentType type,
            SalePayerType payer,
            String sourceRef,
            long amountCents,
            SaleComponentStatus status,
            String cause,
            ZonedDateTime createdAt) {
    }
}
