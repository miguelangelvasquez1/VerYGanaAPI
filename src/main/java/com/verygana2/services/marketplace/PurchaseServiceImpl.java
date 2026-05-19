package com.verygana2.services.marketplace;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.purchase.requests.CreatePurchaseItemRequestDTO;
import com.verygana2.dtos.purchase.requests.CreatePurchaseRequestDTO;
import com.verygana2.dtos.purchase.responses.InitiatePurchaseResponseDTO;
import com.verygana2.dtos.purchase.responses.PurchaseResponseDTO;
import com.verygana2.dtos.wompi.WompiCheckoutRequestDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.exceptions.InsufficientStockException;
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
import com.verygana2.repositories.finance.WompiTransactionRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.repositories.marketplace.ProductStockRepository;
import com.verygana2.repositories.marketplace.PurchaseRepository;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.marketplace.ProductService;
import com.verygana2.services.interfaces.marketplace.PurchaseService;
import com.verygana2.services.wompi.WompiService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseServiceImpl.class);
    
    @Value("${treasury.values.key-value}")
    private Long KEY_VALUE; // 1 llave = 10 COP = 1000 CENTAVOS DE COP

    private final PurchaseRepository purchaseRepository;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;
    private final ConsumerDetailsService consumerDetailsService;
    private final KeyWalletRepository keyWalletRepository;
    private final CopaymentRepository copaymentRepository;
    private final WompiService wompiService;
    private final WompiTransactionRepository wompiTransactionRepository;
    private final PurchaseMapper purchaseMapper;

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
    public PagedResponse<PurchaseResponseDTO> getConsumerPurchases(Long consumerId, Pageable pageable) {
        Page<Purchase> purchases = purchaseRepository.findByConsumerId(consumerId, pageable);
        Page<PurchaseResponseDTO> dtos = purchases.map(purchaseMapper::toPurchaseResponseDTO);
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
     * 3. Validar que keysToUse no supera el máximo permitido por los productos.
     * 4. Reservar las llaves en el KeyWallet del consumidor.
     * 5. Crear Copayment (PENDING) con la parte en efectivo para Wompi.
     *
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
        long totalMaxKeysAllowed = addPurchaseItems(purchase, request);

        // 3. Calcular totales financieros (totalCents, commissionCents,
        // netToCommercialsCents)
        purchase.calculateFinancials();

        // 4. Validar llaves solicitadas
        if (keysToUse > totalMaxKeysAllowed) {
            throw new IllegalArgumentException(String.format(
                    "keysToUse (%d) exceeds the maximum allowed (%d) for this purchase",
                    keysToUse, totalMaxKeysAllowed));
        }

        long keysValueCents = keysToUse * KEY_VALUE;
        long cashAmountCents = purchase.getTotalCents() - keysValueCents;

        // minCashRequired is the cash floor: total minus the maximum keys value usable.
        // Mathematically this is always >= sum(product.minCashCents) across items.
        long minCashRequired = purchase.getTotalCents() - (totalMaxKeysAllowed * KEY_VALUE);
        if (cashAmountCents < minCashRequired) {
            throw new BusinessException(String.format(
                    "Cash payment (%d cents) is below the minimum required (%d cents) for this purchase",
                    cashAmountCents, minCashRequired));
        }

        // 5. Reservar llaves en KeyWallet (solo si el usuario va a usar alguna)
        if (keysToUse > 0) {
            KeyWallet keyWallet = keyWalletRepository.findByConsumerId(consumerId)
                    .orElseThrow(() -> new ObjectNotFoundException(
                            "KeyWallet not found for consumer: " + consumerId, KeyWallet.class));

            if (!keyWallet.hasSufficientPurchaseKeys(keysToUse)) {
                throw new InsufficientFundsException(
                        "Insufficients purchase keys. Available: " + keyWallet.getPurchaseKeys());
            }

            keyWallet.reservePurchaseKeys(keysToUse);
            keyWalletRepository.save(keyWallet);
        }

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
     *
     * @return suma de llaves máximas permitidas en todos los ítems.
     */
    @SuppressWarnings("null")
    private long addPurchaseItems(Purchase purchase, CreatePurchaseRequestDTO request) {
        long totalMaxKeysAllowed = 0;

        for (CreatePurchaseItemRequestDTO itemRequest : request.getItems()) {
            Product product = productService.getById(itemRequest.getProductId());

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new ProductNotAvailableException(product.getName());
            }
            if (product.getAvailableStock() < itemRequest.getQuantity()) {
                throw new InsufficientStockException(product.getName());
            }

            CommercialDetails commercial = product.getCommercial();
            int commissionPct = calculateCommissionPct(commercial);

            for (int i = 0; i < itemRequest.getQuantity(); i++) {
                ProductStock stock = productStockRepository
                        .findNextAvailableForProduct(product.getId())
                        .orElseThrow(() -> new InsufficientStockException(product.getName()));

                stock.markAsReserved();
                productStockRepository.save(stock);

                long unitPriceCents = product.getPriceCents();
                long commissionCents = unitPriceCents * commissionPct / 100;
                long netToCommercial = unitPriceCents - commissionCents;

                PurchaseItem item = PurchaseItem.builder()
                        .product(product)
                        .assignedProductStock(stock)
                        .unitPriceCents(unitPriceCents)
                        .subtotalCents(unitPriceCents)
                        .commissionPctApplied(commissionPct)
                        .commissionCents(commissionCents)
                        .netToCommercialCents(netToCommercial)
                        .maxKeysPctAtPurchase(product.getMaxKeysPct())
                        .status(PurchaseItemStatus.PENDING)
                        .createdAt(ZonedDateTime.now(ZoneOffset.UTC))
                        .build();

                purchase.addItem(item);
                totalMaxKeysAllowed += product.getMaxKeysAllowed();
            }

            product.updateStockCount();
            productRepository.save(product);
        }

        int expectedItems = request.getItems().stream()
                .mapToInt(CreatePurchaseItemRequestDTO::getQuantity).sum();
        if (purchase.getItems().size() != expectedItems) {
            throw new BusinessException("Purchase incomplete: expected "
                    + expectedItems + " items but " + purchase.getItems().size() + " were added");
        }

        purchaseRepository.save(purchase);
        return totalMaxKeysAllowed;
    }

    /**
     * Calcula el porcentaje de comisión que aplica para un comercial concreto.
     *
     * Reglas:
     * - BASIC → 15 %
     * - STANDARD → 10 %
     * - PREMIUM → 5 %
     */
    private int calculateCommissionPct(CommercialDetails commercial) {
        return commercial.getCurrentPlan().getSaleCommissionPct();
    }

    private String resolveDeliveryEmail(CreatePurchaseRequestDTO request, ConsumerDetails consumer) {
        if (request.getContactEmail() != null && !request.getContactEmail().isBlank()) {
            return request.getContactEmail();
        }
        return consumer.getUser().getEmail();
    }
}
