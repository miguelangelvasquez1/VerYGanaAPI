package com.verygana2.services.marketplace;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.ObjectNotFoundException;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.product.responses.FeaturedProductResponseDTO;
import com.verygana2.dtos.purchase.responses.CommercialPendingClaimResponseDTO;
import com.verygana2.dtos.user.commercial.responses.DailySaleResponseDTO;
import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.exceptions.marketplaceExceptions.InvalidClaimException;
import com.verygana2.mappers.marketplace.PurchaseMapper;
import com.verygana2.models.enums.DocumentType;
import com.verygana2.models.enums.marketplace.ProductType;
import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;
import com.verygana2.security.ProductCodeEncryptor;
import com.verygana2.services.interfaces.marketplace.PurchaseItemService;

import jakarta.persistence.EntityNotFoundException;

@Service
public class PurchaseItemServiceImpl implements PurchaseItemService {

    private final PurchaseItemRepository purchaseItemRepository;
    private final PurchaseMapper purchaseMapper;
    private final ProductCodeEncryptor codeEncryptor;
    private final PasswordEncoder passwordEncoder;
    private static final String domain = "https://cdn.verygana.com/public/";
    private static final int MAX_CLAIM_ATTEMPTS = 5;

    /**
     * Hora de corte del payout diario en Colombia — debe coincidir con
     * wompi.payout.cron (por defecto "0 0 23 * * *", 11 PM). Si se cambia el
     * cron, hay que actualizar esto también.
     */
    private static final ZoneId COLOMBIA_TZ = ZoneId.of("America/Bogota");
    private static final LocalTime PAYOUT_CUTOFF_TIME = LocalTime.of(23, 0);

    public PurchaseItemServiceImpl(PurchaseItemRepository purchaseItemRepository, @Lazy PurchaseMapper purchaseMapper, ProductCodeEncryptor codeEncryptor,
            PasswordEncoder passwordEncoder) {
        this.purchaseItemRepository = purchaseItemRepository;
        this.purchaseMapper = purchaseMapper;
        this.codeEncryptor = codeEncryptor;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Long getTotalSalesbyCommercial(Long commercialId) {
        if (commercialId == null) {
            throw new IllegalArgumentException("Commercial id cannot be null");
        }

        if (commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }
        return purchaseItemRepository.countTotalSalesByCommercialId(commercialId);
    }

    @Override
    public List<PurchaseItem> getDeliveredItemsWithoutReview(Long consumerId) {
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        return purchaseItemRepository.findDeliveredItemsWithoutReview(consumerId);
    }

    @Override
    public boolean canUserReviewPurchaseItem(Long purchaseItemId, Long consumerId) {
        if (purchaseItemId == null || purchaseItemId <= 0) {
            throw new IllegalArgumentException("purchaseItem id must be positive");
        }

        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        return purchaseItemRepository.canUserReviewPurchaseItem(purchaseItemId, consumerId);
    }

    @Override
    public PurchaseItem getByIdAndConsumerId(Long purchaseItemId, Long consumerId) {

        if (purchaseItemId == null || purchaseItemId <= 0) {
            throw new IllegalArgumentException("PurchaseItem id must be positive");
        }

        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        return purchaseItemRepository.findByIdAndConsumerId(purchaseItemId, consumerId)
                .orElseThrow(() -> new ObjectNotFoundException("Purchase item with id:" + purchaseItemId + " not found",
                        PurchaseItem.class));
    }
    
    @Override
    public PagedResponse<FeaturedProductResponseDTO> getTopSellingProductsPage(Long commercialId, Pageable pageable) {

        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }

        Page<FeaturedProductResponseDTO> topSellingProducts = purchaseItemRepository.findTopSellingProducts(commercialId, pageable);
        topSellingProducts.forEach(fp -> fp.setImageUrl(domain + fp.getImageUrl()));
        return PagedResponse.from(topSellingProducts);
    }

    @Override
    public BigDecimal getTotalCommercialSalesAmountByDateRange(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate) {
        validateDateRange(commercialId, startDate, endDate);
        return purchaseItemRepository.sumTotalCommercialSalesAmountByMonth(commercialId, startDate, endDate);
    }

    @Override
    public Integer getTotalCommercialSalesByDateRange(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate) {
        validateDateRange(commercialId, startDate, endDate);
        return purchaseItemRepository.countTotalSalesByCommercialIdAndDatesRange(commercialId, startDate, endDate);
    }

    @Override
    public BigDecimal getTotalPlatformComissionsByDateRange(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate) {
        validateDateRange(commercialId, startDate, endDate);
        return purchaseItemRepository.sumTotalPlatformCommissionsByMonth(commercialId, startDate, endDate);
    }

    @Override
    public PagedResponse<FeaturedProductResponseDTO> getTopSellingProductsByDateRangePage(
            Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable) {
        validateDateRange(commercialId, startDate, endDate);
        Page<FeaturedProductResponseDTO> topSellingProducts = purchaseItemRepository
                .findTopSellingProductsByDateRange(commercialId, startDate, endDate, pageable);
        topSellingProducts.forEach(fp -> fp.setImageUrl(domain + fp.getImageUrl()));
        return PagedResponse.from(topSellingProducts);
    }

    @Override
    public PagedResponse<DailySaleResponseDTO> getDailySalesPage(
            Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable) {
        validateDateRange(commercialId, startDate, endDate);
        return PagedResponse.from(purchaseItemRepository
                .findDailySalesByCommercialAndDateRange(commercialId, startDate, endDate, pageable));
    }

    private void validateDateRange(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate) {
        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }

        if (!startDate.isBefore(endDate)) {
            throw new IllegalArgumentException("startDate must be before endDate");
        }
    }

    @Override
    public String getDeliveredCode(Long purchaseItemId, Long consumerId) {
        if (purchaseItemId == null || purchaseItemId <= 0) {
            throw new IllegalArgumentException("PurchaseItem id must be positive");
        }

        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        PurchaseItem item = purchaseItemRepository.findByIdAndConsumerId(purchaseItemId, consumerId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase Item with id:" + purchaseItemId + " not found"));

        if (item.getDeliveredCode() == null) {
            throw new InvalidStatusException("This purchase item has not been delivered yet");
        }

        return codeEncryptor.decrypt(item.getDeliveredCode());
    }

    @Override
    @Transactional
    public void claimPhysicalItem(Long purchaseItemId, Long commercialId, String pin) {
        if (purchaseItemId == null || purchaseItemId <= 0) {
            throw new IllegalArgumentException("PurchaseItem id must be positive");
        }
        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }
        if (pin == null || pin.isBlank()) {
            throw new InvalidClaimException("El PIN es requerido");
        }

        PurchaseItem item = purchaseItemRepository.findById(purchaseItemId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase item with id:" + purchaseItemId + " not found"));

        if (!commercialId.equals(item.getCommercialId())) {
            throw new InvalidClaimException("This purchase item does not belong to your store");
        }

        // Idempotente: un reintento de red sobre un ítem ya reclamado no debe fallar ni reprocesar
        if (item.getStatus() == PurchaseItemStatus.CLAIMED) {
            return;
        }

        if (item.getProduct() == null || item.getProduct().getProductType() != ProductType.PHYSICAL) {
            throw new InvalidClaimException("This purchase item does not require a physical claim PIN");
        }

        if (item.getStatus() != PurchaseItemStatus.PENDING) {
            throw new InvalidClaimException("This purchase item is not awaiting a physical claim");
        }

        if (item.getClaimExpiresAt() != null && ZonedDateTime.now(ZoneOffset.UTC).isAfter(item.getClaimExpiresAt())) {
            throw new InvalidClaimException("The claim window for this purchase item has expired");
        }

        if (item.getClaimAttempts() >= MAX_CLAIM_ATTEMPTS) {
            throw new InvalidClaimException("Maximum PIN attempts exceeded. Contact support.");
        }

        if (item.getClaimPinHash() == null || !passwordEncoder.matches(pin, item.getClaimPinHash())) {
            item.setClaimAttempts(item.getClaimAttempts() + 1);
            purchaseItemRepository.save(item);
            throw new InvalidClaimException("PIN incorrecto");
        }

        item.setStatus(PurchaseItemStatus.CLAIMED);
        item.setClaimedAt(ZonedDateTime.now(ZoneOffset.UTC));
        purchaseItemRepository.save(item);
    }

    @Override
    public PagedResponse<CommercialPendingClaimResponseDTO> getPendingClaims(Long commercialId, DocumentType documentType, String documentNumber, Pageable pageable) {
        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }

        return PagedResponse.from(purchaseItemRepository.findPendingPhysicalItems(commercialId, documentType, documentNumber, pageable).map(purchaseMapper::toCommercialPendingClaimResponseDTO));
    }

    @Override
    public PurchaseItem getReportableItem(Long purchaseItemId, Long consumerId) {
        if (purchaseItemId == null || purchaseItemId <= 0) {
            throw new IllegalArgumentException("PurchaseItem id must be positive");
        }
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        PurchaseItem item = purchaseItemRepository.findByIdAndConsumerId(purchaseItemId, consumerId)
                .orElseThrow(() -> new ObjectNotFoundException("Purchase item with id:" + purchaseItemId + " not found",
                        PurchaseItem.class));

        if (item.getStatus() == PurchaseItemStatus.REFUNDED || item.getStatus() == PurchaseItemStatus.CANCELLED
                || item.getStatus() == PurchaseItemStatus.IN_REVIEW) {
            throw new InvalidStatusException("This purchase item cannot be reported from status: " + item.getStatus());
        }

        if (item.getStatus() == PurchaseItemStatus.CLAIMED && item.getClaimedAt() != null) {
            ZonedDateTime reportDeadline = nextPayoutCutoff(item.getClaimedAt());
            if (ZonedDateTime.now(ZoneOffset.UTC).isAfter(reportDeadline)) {
                throw new InvalidStatusException("The report window for this purchase item has expired");
            }
        }

        return item;
    }

    /**
     * Momento exacto en que el ciclo diario de payouts se llevaría este ítem
     * (la próxima corrida de las 11 PM Colombia estrictamente después de
     * claimedAt) — es la fecha límite real para reportar un problema, ya que
     * después de ese instante el ítem puede quedar pagado al comercial y
     * reversar el dinero ya no sería seguro. Reemplaza una ventana fija de
     * horas: si se reclama a las 10 AM, quedan ~13h para reportar; si se
     * reclama a las 11:30 PM, quedan ~23.5h (hasta la corrida del día siguiente).
     */
    private ZonedDateTime nextPayoutCutoff(ZonedDateTime claimedAt) {
        ZonedDateTime claimedAtColombia = claimedAt.withZoneSameInstant(COLOMBIA_TZ);
        ZonedDateTime cutoffToday = claimedAtColombia.toLocalDate().atTime(PAYOUT_CUTOFF_TIME).atZone(COLOMBIA_TZ);

        ZonedDateTime cutoff = claimedAtColombia.isBefore(cutoffToday) ? cutoffToday : cutoffToday.plusDays(1);
        return cutoff.withZoneSameInstant(ZoneOffset.UTC);
    }

}
