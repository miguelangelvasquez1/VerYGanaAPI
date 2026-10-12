package com.verygana2.services.marketplace;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.purchase.requests.CreatePurchaseItemRequestDTO;
import com.verygana2.dtos.purchase.requests.CreatePurchaseRequestDTO;
import com.verygana2.dtos.purchase.responses.ConsumerPurchaseResponseDTO;
import com.verygana2.dtos.purchase.responses.InitiatePurchaseResponseDTO;
import com.verygana2.dtos.purchase.responses.PurchaseResponseDTO;
import com.verygana2.dtos.wompi.WompiCheckoutRequestDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.exceptions.InsufficientStockException;
import com.verygana2.exceptions.InvalidAmountException;
import com.verygana2.exceptions.KeysReserveInsufficientException;
import com.verygana2.exceptions.ProductNotAvailableException;
import com.verygana2.mappers.marketplace.PurchaseMapper;
import com.verygana2.models.finance.Copayment;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.models.enums.finance.CopaymentStatus;
import com.verygana2.models.enums.finance.WompiTransactionType;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.enums.marketplace.PurchaseStatus;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.finance.CopaymentRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.services.interfaces.finance.KeyLedgerService;
import com.verygana2.repositories.finance.WompiTransactionRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.repositories.marketplace.ProductStockRepository;
import com.verygana2.repositories.marketplace.PurchaseRepository;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.marketplace.KeyCoveragePromotionService;
import com.verygana2.services.interfaces.marketplace.KeyCoverageService;
import com.verygana2.services.interfaces.marketplace.ProductService;
import com.verygana2.services.interfaces.marketplace.PurchaseService;
import com.verygana2.services.wompi.WompiService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseServiceImpl.class);
    
    @Value("${financial.key-value-cents}")
    private Long KEY_VALUE; // 1 llave = 10 COP = 1000 CENTAVOS DE COP

    private final PurchaseRepository purchaseRepository;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;
    private final ConsumerDetailsService consumerDetailsService;
    private final KeyWalletRepository keyWalletRepository;
    private final KeyLedgerService keyLedgerService;
    private final KeyCoverageService keyCoverageService;
    private final KeyCoveragePromotionService keyCoveragePromotionService;
    private final CommissionPolicy commissionPolicy;
    private final CopaymentRepository copaymentRepository;
    private final WompiService wompiService;
    private final WompiTransactionRepository wompiTransactionRepository;
    private final PurchaseMapper purchaseMapper;
    private final TreasuryConfig treasuryConfig;

    // ─── Queries ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Purchase getPurchaseById(Long purchaseId) {
        if (purchaseId == null || purchaseId <= 0) {
            throw new IllegalArgumentException("Purchase id must be positive");
        }
        return purchaseRepository.findById(purchaseId).orElseThrow(
                () -> new ObjectNotFoundException("Purchase with id:" + purchaseId + " not found", Purchase.class));
    }

    @Override
    @Transactional(readOnly = true)
    public Purchase getByIdAndConsumerId(Long purchaseId, Long consumerId) {
        if (purchaseId == null || purchaseId <= 0) {
            throw new IllegalArgumentException("Purchase id must be positive");
        }
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }
        return purchaseRepository.findByIdAndConsumerId(purchaseId, consumerId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Purchase with id: " + purchaseId + " and consumer id: " + consumerId + " not found",
                        Purchase.class));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ConsumerPurchaseResponseDTO> getConsumerPurchases(Long consumerId, Pageable pageable) {
        Page<Purchase> purchases = purchaseRepository.findByConsumerId(consumerId, pageable);
        Page<ConsumerPurchaseResponseDTO> dtos = purchases.map(purchaseMapper::toConsumerPurchaseResponseDTO);
        return PagedResponse.from(dtos);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseResponseDTO getPurchaseResponseDTO(Long purchaseId, Long consumerId) {
        Purchase purchase = purchaseRepository.findByIdAndConsumerIdWithItems(purchaseId, consumerId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Purchase with id:" + purchaseId + " not found", Purchase.class));
        return purchaseMapper.toPurchaseResponseDTO(purchase);
    }

    // ─── Creación de compra ───────────────────────────────────────────────────

    /**
     * Inicia una compra en estado PENDING y crea el Copayment asociado.
     *
     * Flujo:
     * 1. Validar ítems y reservar stock (no marcar como vendido todavía).
     * 2. Calcular comisiones por ítem según plan del empresario
     * 3. Validar que keysToUse no supera la cobertura máxima (50 % ordinario, o el
     *    tope de una promoción especial vigente), ni el saldo, ni el respaldo.
     * 4. Reservar las llaves en el Libro Mayor y el presupuesto de la promoción.
     * 5. Crear Copayment (PENDING) con la parte en efectivo para Wompi.
     * 6. Revisar si el usuario es apto para brindarle tickets
     * La compra se completa cuando el webhook de Wompi confirma el pago.
     */
    @Override
    @SuppressWarnings("null")
    public InitiatePurchaseResponseDTO createPurchase(Long consumerId, CreatePurchaseRequestDTO request) {

        log.info("Validating request items size...");

        if (request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Purchase must have at least one item");
        }

        long keysToUse = request.getKeysToUse() != null ? request.getKeysToUse() : 0L;

        ConsumerDetails consumer = consumerDetailsService.getConsumerById(consumerId);

        // 1. Crear compra base y persistir para obtener ID
        String referenceId = UUID.randomUUID().toString();

        log.info("Creating purchase for consumer: {}", consumerId);

        Purchase purchase = Purchase.builder()
                .referenceId(referenceId)
                .consumer(consumer)
                .deliveryEmail(resolveDeliveryEmail(request, consumer))
                .build();
        purchase = purchaseRepository.save(purchase);

        // 2. Agregar ítems: reserva stock + calcula comisiones
        addPurchaseItems(purchase, request);

        // 3. Calcular totales financieros (totalCents, commissionCents,
        // netToCommercialsCents)
        purchase.calculateFinancials();

        // 4. Porción en Llaves (Contrato B 2.26): tope de cada unidad, ordinario o de
        // promoción especial. Pedir más de lo permitido se rechaza en vez de recortarse:
        // recortar cambiaría el dinero que se le cobra al usuario sin que lo sepa. Lo
        // que sí puede aplicar se lo dice antes POST /purchases/quote.
        KeyCoveragePlan coverage = keyCoverageService.planFor(consumerId,
                purchase.getItems().stream().map(PurchaseItem::getProduct).toList());

        long keysValueCents = keysToUse * KEY_VALUE;
        long maxKeysAllowed = coverage.maxCoverageCents() / KEY_VALUE;
        if (keysToUse > maxKeysAllowed) {
            throw new InvalidAmountException(String.format(
                    "keysToUse (%d) exceeds the maximum allowed (%d) for this purchase",
                    keysToUse, maxKeysAllowed));
        }
        long cashAmountCents = purchase.getTotalCents() - keysValueCents;

        // 5. Bloquear la billetera y validar saldo y respaldo (solo si el usuario va a
        // usar llaves). Las llaves ya vencidas que el job aún no extinguió no cuentan.
        KeyWallet keyWallet = null;
        if (keysToUse > 0) {
            keyWallet = keyWalletRepository.findByConsumerIdForUpdate(consumerId)
                    .orElseThrow(() -> new ObjectNotFoundException(
                            "KeyWallet not found for consumer: " + consumerId, KeyWallet.class));

            long spendableCents = keyLedgerService.spendablePurchaseCents(keyWallet);
            if (spendableCents < keysValueCents) {
                throw new InsufficientFundsException(
                        "Insufficients purchase keys. Available: " + spendableCents / KEY_VALUE);
            }

            // El respaldo se mira aquí, antes de que Wompi cobre. Tesorería lo vuelve a
            // comprobar al aprobar, pero rechazar en ese punto deja al usuario cobrado
            // y sin compra.
            if (keysValueCents > keyCoverageService.backingAvailableCents(coverage)) {
                throw new KeysReserveInsufficientException();
            }
        }

        applyCoverage(purchase, coverage, keysValueCents);

        // La promoción especial compromete su presupuesto junto con las llaves. Va
        // después de la billetera: es el orden de bloqueo de todos los flujos.
        keyCoveragePromotionService.reserveForPurchase(purchase);

        // 6. Persistir snapshot financiero en Purchase
        purchase.setKeysValueCents(keysValueCents);
        purchase.setCashCents(cashAmountCents);
        purchase = purchaseRepository.save(purchase);

        // 7. Crear Copayment PENDING
        Copayment copayment = Copayment.builder()
                .purchase(purchase)
                .consumer(consumer)
                .keysUsed(keysToUse)
                .keysValueCents(keysValueCents)
                .cashAmountCents(cashAmountCents)
                .totalAmountCents(purchase.getTotalCents())
                .status(CopaymentStatus.PENDING)
                .build();
        copayment = copaymentRepository.save(copayment);

        // Reservar las llaves en el Libro Mayor. Va después de crear el copago porque
        // el asiento de reserva lo referencia: es lo que luego permite confirmarlo o
        // liberarlo una sola vez.
        if (keyWallet != null) {
            keyLedgerService.reserveForCopayment(keyWallet, keysValueCents, copayment.getId());
        }

        // 8. Generar URL de checkout de Wompi y vincular el WompiTransaction al Copayment
        String redirectUrl = request.getRedirectUrl() != null && !request.getRedirectUrl().isBlank()
                ? request.getRedirectUrl()
                : "https://verygana.com/purchases/" + purchase.getId();

        WompiCheckoutResponseDTO checkoutResponse = wompiService.createCheckoutUrl(
                WompiCheckoutRequestDTO.builder()
                        .reference(referenceId)
                        .amountInCents(cashAmountCents)
                        .customerEmail(purchase.getDeliveryEmail())
                        .redirectUrl(redirectUrl)
                        .build(),
                WompiTransactionType.CHARGE_COPAYMENT);

        // Vincular el WompiTransaction pre-registrado al Copayment
        WompiTransaction wompiTx = wompiTransactionRepository.findByReference(referenceId)
                .orElseThrow(() -> new IllegalStateException(
                        "WompiTransaction pre-registrada no encontrada para reference: " + referenceId));
        copayment.setWompiTransaction(wompiTx);
        copaymentRepository.save(copayment);

        log.info("Purchase initiated (PENDING). id={}, referenceId={}, totalCents={}, cashAmountCents={}",
                purchase.getId(), referenceId, purchase.getTotalCents(), cashAmountCents);

        return new InitiatePurchaseResponseDTO(
                purchase.getId(),
                referenceId,
                cashAmountCents,
                purchase.getTotalCents(),
                keysValueCents,
                PurchaseStatus.PENDING,
                checkoutResponse.getCheckoutUrl(),
                Instant.now());
    }

    // ─── Métodos privados ─────────────────────────────────────────────────────

    /**
     * Agrega ítems a la compra reservando stock y calculando comisiones.
     */
    @SuppressWarnings("null")
    private void addPurchaseItems(Purchase purchase, CreatePurchaseRequestDTO request) {

        for (CreatePurchaseItemRequestDTO itemRequest : request.getItems()) {
            Product product = productService.getById(itemRequest.getProductId());

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new ProductNotAvailableException(product.getName());
            }
            if (product.getAvailableStock() < itemRequest.getQuantity()) {
                throw new InsufficientStockException(product.getName());
            }

            CommercialDetails commercial = product.getCommercial();
            int commissionPct = commissionPolicy.commissionPctFor(commercial);

            for (int i = 0; i < itemRequest.getQuantity(); i++) {
                ProductStock stock = productStockRepository
                        .findNextAvailableForProduct(product.getId())
                        .orElseThrow(() -> new InsufficientStockException(product.getName()));

                stock.markAsReserved();
                productStockRepository.save(stock);

                long unitPriceCents = product.getPriceCents();

                PurchaseItem item = PurchaseItem.builder()
                        .product(product)
                        .productNameSnapshot(product.getName())
                        .commercialId(commercial.getId())
                        .assignedProductStock(stock)
                        .unitPriceCents(unitPriceCents)
                        .subtotalCents(unitPriceCents)
                        .commissionPctApplied(commissionPct)
                        .commercialActivityTypeAtPurchase(commercial.getCommercialActivityType())
                        .maxKeysPctAtPurchase(product.getMaxKeysPct())
                        .status(PurchaseItemStatus.PENDING)
                        .createdAt(ZonedDateTime.now(ZoneOffset.UTC))
                        .build();
                // Comisión provisional sobre el precio completo: la definitiva se fija al
                // aprobarse el pago, descontando lo que absorba el Saldo de Prosperidad
                // (ProsperityService.absorbPurchase).
                item.applyCommission(unitPriceCents, treasuryConfig.getVatPct());

                purchase.addItem(item);
            }

            product.updateStockCount();

            // La sección de recompensas de juegos no filtra por stock (ver
            // ProductRepository.findGameRewardsProducts), así que hay que apagar
            // isGameReward manualmente al agotarse para no ofrecer un producto
            // sin unidades disponibles.
            if (Boolean.TRUE.equals(product.getIsGameReward()) && product.getAvailableStock() == 0) {
                product.setIsGameReward(false);
                product.setGameRewardAutoDisabled(true);
            }

            productRepository.save(product);
        }

        int expectedItems = request.getItems().stream()
                .mapToInt(CreatePurchaseItemRequestDTO::getQuantity).sum();
        if (purchase.getItems().size() != expectedItems) {
            throw new BusinessException("Purchase incomplete: expected "
                    + expectedItems + " items but " + purchase.getItems().size() + " were added");
        }

        purchaseRepository.save(purchase);
    }

    /**
     * Deja en cada ítem su Porción en Llaves, el tope que le rigió y, si lo hubo, el
     * exceso que tomó de una promoción especial. Con esto el reparto llaves/dinero deja
     * de existir solo a nivel de copago: un reembolso devuelve exactamente lo de su ítem.
     */
    private void applyCoverage(Purchase purchase, KeyCoveragePlan coverage, long keysValueCents) {
        List<KeyCoveragePlan.Allocation> allocations = coverage.allocate(keysValueCents);
        for (int i = 0; i < purchase.getItems().size(); i++) {
            PurchaseItem item = purchase.getItems().get(i);
            KeyCoveragePlan.Unit unit = coverage.units().get(i);
            KeyCoveragePlan.Allocation allocation = allocations.get(i);

            item.setMaxKeysPctAtPurchase(unit.pctApplied());
            item.setKeysPctApplied(unit.pctApplied());
            item.setKeysValueCents(allocation.keysCents());
            item.setPromotionExcessCents(allocation.promotionExcessCents());
            // La promoción queda en el ítem solo si de verdad se usó su margen.
            item.setKeyCoveragePromotionId(allocation.promotionExcessCents() > 0 ? unit.promotionId() : null);
        }
    }

    private String resolveDeliveryEmail(CreatePurchaseRequestDTO request, ConsumerDetails consumer) {
        if (request.getContactEmail() != null && !request.getContactEmail().isBlank()) {
            return request.getContactEmail();
        }
        return consumer.getUser().getEmail();
    }

}
