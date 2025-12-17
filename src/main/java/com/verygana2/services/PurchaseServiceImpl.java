package com.verygana2.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.purchase.requests.CreatePurchaseItemRequestDTO;
import com.verygana2.dtos.purchase.requests.CreatePurchaseRequestDTO;
import com.verygana2.dtos.purchase.responses.PurchaseResponseDTO;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.exceptions.InsufficientStockException;
import com.verygana2.exceptions.ProductNotAvailableException;
import com.verygana2.mappers.PurchaseMapper;
import com.verygana2.models.Transaction;
import com.verygana2.models.Wallet;
import com.verygana2.models.enums.PurchaseItemStatus;
import com.verygana2.models.enums.PurchaseStatus;
import com.verygana2.models.products.Product;
import com.verygana2.models.products.ProductStock;
import com.verygana2.models.products.Purchase;
import com.verygana2.models.products.PurchaseItem;
import com.verygana2.models.userDetails.SellerDetails;
import com.verygana2.repositories.ProductRepository;
import com.verygana2.repositories.ProductStockRepository;
import com.verygana2.repositories.PurchaseRepository;
import com.verygana2.repositories.TransactionRepository;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.PlatformTreasuryService;
import com.verygana2.services.interfaces.ProductService;
import com.verygana2.services.interfaces.PurchaseService;
import com.verygana2.services.interfaces.WalletService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {

    private final WalletService walletService;
    private final TransactionRepository transactionRepository;
    private final PurchaseRepository purchaseRepository;
    private final PlatformTreasuryService platformTreasuryService;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final ConsumerDetailsService consumerDetailsService;
    private final ProductStockRepository productStockRepository;
    private final EmailService emailService;
    private final PurchaseMapper purchaseMapper;

    private static final Logger log = LoggerFactory.getLogger(PurchaseServiceImpl.class);

    @Value("${platformEarnings.product-commission}")
    private BigDecimal platformCommission;

    @Value("${taxes.iva}")
    private BigDecimal iva;

    @Override
    public Purchase getPurchaseById(Long purchaseId) {
        if (purchaseId == null || purchaseId <= 0) {
            throw new IllegalArgumentException("Purchase id must be positive");
        }

        return purchaseRepository.findById(purchaseId).orElseThrow(
                () -> new ObjectNotFoundException("Purchase with id:" + purchaseId + " not found", Purchase.class));
    }

    @Override
    @SuppressWarnings("null")
    public EntityCreatedResponseDTO createPurchase(Long consumerId, CreatePurchaseRequestDTO request) {

        log.info("Validating request items size...");
        if (request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Purchase must have at least one item");
        }

        String referenceId = UUID.randomUUID().toString();

        log.info("Creating purchase for consumer: {}", consumerId);

        // 1. Crear compra
        Purchase basePurchase = createBasePurchase(referenceId, consumerId, request);

        // 2. guardamos el cuerpo de la compra para generar ID
        Purchase savedBasePurchase = purchaseRepository.save(basePurchase);

        // 3. Adicionar items
        Purchase purchaseWithItems = addPurchaseItems(savedBasePurchase, request);

        // 4. Calcular total de la compra
        purchaseWithItems.calculateTotals();

        // logica para los cupones

        // 5. Validar saldo del comprador
        Wallet buyerWallet = walletService.getByOwnerId(consumerId);
        if (!buyerWallet.hasSufficientBalance(purchaseWithItems.getTotal())) {
            releaseCodes(purchaseWithItems);
            throw new InsufficientFundsException("Insufficient Tpoints to complete purchase");
        }

        // Bloquear el saldo mientras se procesa
        buyerWallet.blockBalance(purchaseWithItems.getTotal());

        // 6. Procesar pago (desbloquear y transferir)
        try {
            processPurchasePayment(purchaseWithItems, buyerWallet);
            purchaseWithItems.setStatus(PurchaseStatus.COMPLETED);
        } catch (Exception e) {
            // Si falla, desbloquear el saldo
            buyerWallet.unblockBalance(purchaseWithItems.getTotal());
            purchaseWithItems.setStatus(PurchaseStatus.FAILED);

            // ⭐ LIBERAR LOS CÓDIGOS
            releaseCodes(purchaseWithItems);

            throw e;
        }

        Purchase finalPurchase = purchaseRepository.save(purchaseWithItems);

        // Envio de productos al comprador (emailService)
        try {
            emailService.sendPurchaseConfirmation(finalPurchase, request.getContactEmail());
        } catch (Exception e) {
            log.error("Failed to send purchase confirmation email, but purchase was successful. Purchase ID: {}",
                    finalPurchase.getId(), e);
            // No se lanza la excepción porque el pago ya se completó exitosamente
        }

        log.info("Purchase created succesfully. id: {}, Total: {}", finalPurchase.getId(),
                finalPurchase.getTotal());

        return new EntityCreatedResponseDTO(finalPurchase.getId(), "Purchase registered succesfully", Instant.now());
    }

    // Metodos privados para crear una compra
    private Purchase createBasePurchase(String referenceId, Long consumerId, CreatePurchaseRequestDTO request) {
        return Purchase.builder()
                .referenceId(referenceId)
                .consumer(consumerDetailsService.getConsumerById(consumerId))
                .status(PurchaseStatus.PENDING)
                .contactEmail(request.getContactEmail())
                .notes(request.getNotes())
                .couponCode(request.getCouponCode())
                .build();
    }

    private Purchase addPurchaseItems(Purchase savedPurchase, CreatePurchaseRequestDTO request) {

        for (CreatePurchaseItemRequestDTO itemRequest : request.getItems()) {
            Product product = productService.getById(itemRequest.getProductId());

            if (!product.isActive()) {
                throw new ProductNotAvailableException(product.getName());
            }
            // Validar stock
            if (product.getAvailableStock() < itemRequest.getQuantity()) {
                throw new InsufficientStockException(product.getName());
            }

            for (int i = 0; i < itemRequest.getQuantity(); i++) {
                ProductStock availableCode = productStockRepository.findNextAvailableForProduct(product.getId())
                .orElseThrow(() -> new InsufficientStockException(product.getName()));

                availableCode.markAsReserved();
                productStockRepository.saveAndFlush(availableCode);
                // Crear item
                PurchaseItem purchaseItem = PurchaseItem.builder()
                        .purchase(savedPurchase)
                        .product(product)
                        .quantity(1)
                        .priceAtPurchase(product.getPrice())
                        .status(PurchaseItemStatus.PENDING)
                        .build();

                // Asignar código
                purchaseItem.assignProductStock(availableCode);

                // Agregar a la compra (esto lo agrega a la lista)
                savedPurchase.addItem(purchaseItem);
            }

            product.updateStockCount();
            productRepository.save(product);

        }

        int expectedItems = request.getItems().stream().mapToInt(CreatePurchaseItemRequestDTO::getQuantity).sum();

        if (savedPurchase.getItems().size() != expectedItems) {
            throw new BusinessException("Purchase incompleted");
        }

        savedPurchase = purchaseRepository.save(savedPurchase);

        for(PurchaseItem item : savedPurchase.getItems()){
            if (item.getAssignedProductStock() != null) {
                ProductStock stock = item.getAssignedProductStock();
                stock.markAsSold(item);
                productStockRepository.save(stock);
            }
        }

        return savedPurchase;
    }

    private void releaseCodes(Purchase purchaseWithItems) {
        for (PurchaseItem item : purchaseWithItems.getItems()) {
            if (item.getAssignedProductStock() != null) {
                item.getAssignedProductStock().markAsAvailable();
                productStockRepository.save(Objects.requireNonNull(item.getAssignedProductStock()));
            }

            // Restaurar stock del producto
            Product product = item.getProduct();
            product.updateStockCount();
            productRepository.save(product);
        }
    }

    private void processPurchasePayment(Purchase purchaseWithItems, Wallet buyerWallet) {
        String referenceId = purchaseWithItems.getReferenceId();
        BigDecimal totalAmount = purchaseWithItems.getTotal();

        // 1. Desbloquear y restar saldo del comprador
        log.debug("Debiting {} from buyer wallet {}", totalAmount, buyerWallet.getId());
        buyerWallet.unblockBalance(totalAmount);
        buyerWallet.subtractBalance(totalAmount);

        // 2. Crear transacción del comprador (débito)
        Transaction buyerTx = Transaction.createWholePurchaseTransaction(
                buyerWallet,
                totalAmount.negate(), // Negativo porque es débito
                referenceId);
        transactionRepository.save(Objects.requireNonNull(buyerTx));
        log.debug("Buyer transaction saved: {}", buyerTx.getId());

        // 3. Agrupar items por vendedor
        Map<SellerDetails, BigDecimal> sellerAmounts = groupItemsBySeller(purchaseWithItems);

        // 4. Distribuir a cada vendedor
        log.info("Distributing payments to {} seller(s)", sellerAmounts.size());
        distributeSellersEarningsAndTransactions(purchaseWithItems, referenceId, sellerAmounts);

        BigDecimal totalplatformEarnings = purchaseWithItems.getPlatformEarnings();
        BigDecimal totalPaidToSellers = purchaseWithItems.getPaidToSellers();

        // 5. ✅ Registrar UNA SOLA PlatformTransaction con el total de comisiones
        savePurchaseDataInPlatform(purchaseWithItems, referenceId, totalplatformEarnings, totalPaidToSellers,
                sellerAmounts.size());
        log.info("Payment processed successfully for purchase: {}", purchaseWithItems.getId());
    }

    // Metodos para procesar una compra
    private Map<SellerDetails, BigDecimal> groupItemsBySeller(Purchase purchase) {
        Map<SellerDetails, BigDecimal> sellerAmounts = new HashMap<>();

        for (PurchaseItem item : purchase.getItems()) {
            SellerDetails seller = item.getProduct().getSeller();
            BigDecimal currentAmount = sellerAmounts.getOrDefault(seller, BigDecimal.ZERO);
            sellerAmounts.put(seller, currentAmount.add(item.getSubtotal()));
        }

        return sellerAmounts;
    }

    private void distributeSellersEarningsAndTransactions(Purchase purchaseWithItems, String referenceId,
            Map<SellerDetails, BigDecimal> sellerAmounts) {

        for (Map.Entry<SellerDetails, BigDecimal> entry : sellerAmounts.entrySet()) {
            SellerDetails seller = entry.getKey();
            BigDecimal grossAmount = entry.getValue();

            // Calcular comisión que debe pagar cada vendedor y monto neto que recibe
            BigDecimal commission = grossAmount.multiply(platformCommission);
            BigDecimal netAmount = grossAmount.subtract(commission);

            purchaseWithItems.updatePlatformEarnings(commission);
            purchaseWithItems.updatePaidToSellers(netAmount);

            // Agregar saldo al vendedor
            Wallet sellerWallet = walletService.getByOwnerId(seller.getUser().getId());
            sellerWallet.addBalance(netAmount);

            // Crear transacción del vendedor
            Transaction sellerTx = Transaction.createProductSaleTransaction(
                    sellerWallet,
                    netAmount,
                    referenceId);
            transactionRepository.save(Objects.requireNonNull(sellerTx));
        }
    }

    private void savePurchaseDataInPlatform(Purchase purchaseWithItems, String referenceId, BigDecimal platformEarnings,
            BigDecimal totalPaidToSellers, int sellerAmountsSize) {

        String description = String.format(
                "Commission from purchase #%d - Totals earnings: %s from %d seller(s)",
                purchaseWithItems.getId(),
                platformEarnings,
                sellerAmountsSize);

        platformTreasuryService.addProductsSaleCommission(
                platformEarnings,
                referenceId,
                description);

        String description2 = String.format("Amount reserved from purchase #%d - Total reserved: %s from %d seller(s)",
                purchaseWithItems.getId(), totalPaidToSellers, sellerAmountsSize);

        platformTreasuryService.addForWithdrawals(totalPaidToSellers, description2);
    }

    // Fin de metodos para procesar una compra
    // Fin de metodos para crear una compra

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getPurchaseTransactions(Long purchaseId) {
        Purchase purchase = purchaseRepository.findById(Objects.requireNonNull(purchaseId))
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Purchase with id: " + purchaseId + " not found",
                        Purchase.class));

        return transactionRepository.findByReferenceId(purchase.getReferenceId());
    }

    @Override
    public PagedResponse<PurchaseResponseDTO> getConsumerPurchases(Long consumerId, Pageable pageable) {
        Page<Purchase> purchases = purchaseRepository.findByConsumerId(consumerId, pageable);
        Page<PurchaseResponseDTO> purchasesDtos = purchases.map(purchaseMapper::toPurchaseResponseDTO);
        return PagedResponse.from(purchasesDtos);
    }

    @Override
    public PurchaseResponseDTO getPurchaseResponseDTO(Long purchaseId, Long consumerId) {
        Purchase purchase = purchaseRepository.findByIdAndConsumerIdWithItems(purchaseId, consumerId).orElseThrow(() -> new ObjectNotFoundException("Purchase with id:" + purchaseId + " not found", Purchase.class));
        return purchaseMapper.toPurchaseResponseDTO(purchase);
    }
}
