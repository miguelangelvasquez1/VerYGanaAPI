package com.verygana2.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.purchase.requests.CreatePurchaseItemRequestDTO;
import com.verygana2.dtos.purchase.requests.CreatePurchaseRequestDTO;
import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.exceptions.InsufficientStockException;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.UnauthorizedException;
import com.verygana2.models.Transaction;
import com.verygana2.models.Wallet;
import com.verygana2.models.enums.PurchaseItemStatus;
import com.verygana2.models.enums.products.PaymentMethod;
import com.verygana2.models.enums.products.PurchaseStatus;
import com.verygana2.models.products.Product;
import com.verygana2.models.products.Purchase;
import com.verygana2.models.products.PurchaseItem;
import com.verygana2.models.userDetails.SellerDetails;
import com.verygana2.repositories.ProductRepository;
import com.verygana2.repositories.PurchaseRepository;
import com.verygana2.repositories.TransactionRepository;
import com.verygana2.services.interfaces.PlatformTreasuryService;
import com.verygana2.services.interfaces.ProductService;
import com.verygana2.services.interfaces.PurchaseService;
import com.verygana2.services.interfaces.WalletService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.dtos.generic.EntityCreatedResponse;

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
    
    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.10"); // 10%

    @Override
    public EntityCreatedResponse createPurchase(Long consumerId, CreatePurchaseRequestDTO request) {

        // 1. Crear la compra
        Purchase purchase = new Purchase();
        purchase.setConsumer(consumerDetailsService.getConsumerById(consumerId));
        purchase.setStatus(PurchaseStatus.PENDING_PAYMENT);
        purchase.setPaymentMethod(PaymentMethod.WALLET);
        purchase.setDeliveryAddress(request.getDeliveryAddress());
        purchase.setDeliveryCity(request.getDeliveryCity());
        purchase.setDeliveryDepartment(request.getDeliveryDepartment());
        purchase.setDeliveryPhone(request.getDeliveryPhone());
        purchase.setDeliveryNotes(request.getDeliveryNotes());

        // 2. Agregar items
        for (CreatePurchaseItemRequestDTO itemRequest : request.getItems()) {
            Product product = productService.getById(itemRequest.getProductId());
            
            // Validar stock
            if (product.getStock() < itemRequest.getQuantity()) {
                throw new InsufficientStockException(product.getName(), product.getStock());
            }

            PurchaseItem item = new PurchaseItem();
            item.setProduct(product);
            item.setSeller(product.getSeller());
            item.setQuantity(itemRequest.getQuantity());
            item.setPriceAtPurchase(product.getPrice());
            item.setProductName(product.getName());
            item.setProductImageUrl(product.getPrincipalImageUrl());

            purchase.addItem(item);
        }

        // 3. Calcular totales
        purchase.calculateTotals();

        // 4. Validar saldo del comprador
        Wallet buyerWallet = walletService.getByOwnerId(consumerId);
        if (!buyerWallet.hasSufficientBalance(purchase.getTotalAmount())) {
            throw new InsufficientFundsException("Insufficient Tpoints to complete purchase");
        }

        // Bloquear el saldo mientras se procesa
        buyerWallet.blockBalance(purchase.getTotalAmount());

        // 5. Guardar la compra (genera el ID y referenceId)
        purchase = purchaseRepository.save(purchase);

        // 6. Procesar pago (desbloquear y transferir)
        try {
            processPurchasePayment(purchase, buyerWallet);
            purchase.setStatus(PurchaseStatus.PAID);
        } catch (Exception e) {
            // Si falla, desbloquear el saldo
            buyerWallet.unblockBalance(purchase.getTotalAmount());
            purchase.setStatus(PurchaseStatus.CANCELLED);
            purchaseRepository.save(purchase);
            throw e;
        }

        // 7. Actualizar inventarios
        updateInventories(purchase);
        purchaseRepository.save(purchase);

        return new EntityCreatedResponse("Purchase registered succesfully", Instant.now());
    }

    private void processPurchasePayment(Purchase purchase, Wallet buyerWallet) {
        String referenceId = purchase.getReferenceId();
        BigDecimal totalAmount = purchase.getTotalAmount();

        // 1. Desbloquear y restar saldo del comprador
        buyerWallet.unblockBalance(totalAmount);
        buyerWallet.subtractBalance(totalAmount);

        // 2. Crear transacción del comprador (débito)
        Transaction buyerTx = Transaction.createWholePurchaseTransaction(
            buyerWallet.getId(),
            totalAmount.negate(), // Negativo porque es débito
            referenceId
        );
        transactionRepository.save(buyerTx);

        // 3. Agrupar items por vendedor
        Map<SellerDetails, BigDecimal> sellerAmounts = groupItemsBySeller(purchase);

        BigDecimal totalCommission = BigDecimal.ZERO;
        BigDecimal totalPaidToSellers = BigDecimal.ZERO;

        // 4. Distribuir a cada vendedor
        for (Map.Entry<SellerDetails, BigDecimal> entry : sellerAmounts.entrySet()) {
            SellerDetails seller = entry.getKey();
            BigDecimal grossAmount = entry.getValue();

            // Calcular comisión y monto neto
            BigDecimal commission = grossAmount.multiply(COMMISSION_RATE);
            BigDecimal netAmount = grossAmount.subtract(commission);

            totalCommission = totalCommission.add(commission); // ✅ FIX: Acumular correctamente
            totalPaidToSellers = totalPaidToSellers.add(netAmount);

            // Agregar saldo al vendedor
            Wallet sellerWallet = walletService.getByOwnerId(seller.getUser().getId());
            sellerWallet.addBalance(netAmount);

            // Crear transacción del vendedor
            Transaction sellerTx = Transaction.createProductSaleTransaction(
                sellerWallet.getId(),
                netAmount,
                referenceId
            );
            transactionRepository.save(sellerTx);
        }

        // 5. ✅ Registrar UNA SOLA PlatformTransaction con el total de comisiones
        String description = String.format(
            "Commission from purchase #%d - Total commission: %s from %d seller(s)",
            purchase.getId(),
            totalCommission,
            sellerAmounts.size()
        );
        
        platformTreasuryService.addProductsSaleCommission(
            totalCommission, 
            referenceId,
            description
        );
        String description2 = String.format("Amount reserved from purchase #%d - Total reserved: %s from %d seller(s)", purchase.getId(), totalPaidToSellers, sellerAmounts.size());
        platformTreasuryService.addForWithdrawals(totalPaidToSellers, description2);
    }

    private Map<SellerDetails, BigDecimal> groupItemsBySeller(Purchase purchase) {
        Map<SellerDetails, BigDecimal> sellerAmounts = new HashMap<>();
        
        for (PurchaseItem item : purchase.getItems()) {
            SellerDetails seller = item.getSeller();
            BigDecimal currentAmount = sellerAmounts.getOrDefault(seller, BigDecimal.ZERO);
            sellerAmounts.put(seller, currentAmount.add(item.getSubtotal()));
        }
        
        return sellerAmounts;
    }
    
    private void updateInventories(Purchase purchase) {
        for (PurchaseItem item : purchase.getItems()) {
            Product product = item.getProduct();
            product.decrementStock(item.getQuantity());
            productRepository.save(product);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getPurchaseTransactions(Long purchaseId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
            .orElseThrow(() -> new ObjectNotFoundException(
                "Purchase with id: " + purchaseId + " not found", 
                Purchase.class
            ));

        return transactionRepository.findByReferenceId(purchase.getReferenceId());
    }

    @Override
    public void cancelPurchase(Long purchaseId, Long userId, String reason) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
            .orElseThrow(() -> new ObjectNotFoundException(
                "Purchase with id: " + purchaseId + " not found", 
                Purchase.class
            ));
        
        // Validar que sea el comprador
        if (!purchase.getConsumer().getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You can only cancel your own purchases");
        }
        
        // Validar estado
        if (!purchase.getStatus().equals(PurchaseStatus.PAID)) {
            throw new BusinessException("Can only cancel paid purchases");
        }
        
        // Revertir todo
        refundPurchase(purchase, reason);
        
        // Actualizar estado
        purchase.setStatus(PurchaseStatus.CANCELLED);
        purchase.setCancelledDate(LocalDateTime.now());
        purchase.setCancellationReason(reason);
        purchaseRepository.save(purchase);
    }
    
    /**
     * Cancela un item específico de una compra
     */
    @Override
    public void cancelPurchaseItem(Long purchaseId, Long itemId, Long userId, String reason) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
            .orElseThrow(() -> new ObjectNotFoundException(
                "Purchase not found", 
                Purchase.class
            ));
        
        // Validar que sea el comprador
        if (!purchase.getConsumer().getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You can only cancel your own purchases");
        }
        
        // Encontrar el item
        PurchaseItem item = purchase.getItems().stream()
            .filter(i -> i.getId().equals(itemId))
            .findFirst()
            .orElseThrow(() -> new ObjectNotFoundException(
                "Purchase item not found", 
                PurchaseItem.class
            ));
        
        // Validar estado
        if (!item.getStatus().equals(PurchaseItemStatus.PENDING)) {
            throw new BusinessException("Can only cancel pending items");
        }
        
        // Revertir el item
        refundPurchaseItem(purchase, item, reason);
        
        // Actualizar estado del item
        item.setStatus(PurchaseItemStatus.CANCELLED);
        
        // Si todos los items están cancelados, cancelar la compra completa
        boolean allCancelled = purchase.getItems().stream()
            .allMatch(i -> i.getStatus().equals(PurchaseItemStatus.CANCELLED));
        
        if (allCancelled) {
            purchase.setStatus(PurchaseStatus.CANCELLED);
            purchase.setCancelledDate(LocalDateTime.now());
            purchase.setCancellationReason("All items cancelled");
        }
        
        purchaseRepository.save(purchase);
    }
    
    /**
     * Reembolsa una compra completa
     */
    @Transactional
    protected void refundPurchase(Purchase purchase, String reason) {
        String referenceId = purchase.getReferenceId();
        String refundReferenceId = "REFUND-" + referenceId;
        BigDecimal totalAmount = purchase.getTotalAmount();
        
        // 1. Devolver dinero al comprador
        Wallet buyerWallet = walletService.getByOwnerId(purchase.getConsumer().getUser().getId());
        buyerWallet.addBalance(totalAmount);
        
        Transaction buyerRefundTx = Transaction.createWholePurchaseRefundTransaction(
            buyerWallet.getId(),
            totalAmount,  // Positivo = crédito
            refundReferenceId
        );
        transactionRepository.save(buyerRefundTx);
        
        // 2. Quitar dinero de vendedores
        Map<SellerDetails, BigDecimal> sellerAmounts = groupItemsBySeller(purchase);
        BigDecimal totalCommissionToRefund = BigDecimal.ZERO;
        
        for (Map.Entry<SellerDetails, BigDecimal> entry : sellerAmounts.entrySet()) {
            SellerDetails seller = entry.getKey();
            BigDecimal grossAmount = entry.getValue();
            
            BigDecimal commission = grossAmount.multiply(COMMISSION_RATE);
            BigDecimal netAmount = grossAmount.subtract(commission);
            
            totalCommissionToRefund = totalCommissionToRefund.add(commission);
            
            // Quitar del vendedor
            Wallet sellerWallet = walletService.getByOwnerId(seller.getUser().getId());
            sellerWallet.subtractBalance(netAmount);
            
            Transaction sellerRefundTx = Transaction.createProductSaleRefundTransaction(
                sellerWallet.getId(),
                netAmount.negate(),  // Negativo = débito
                refundReferenceId
            );
            transactionRepository.save(sellerRefundTx);
        }
        
        // 3. ✅ Restar comisión de la tesorería (transacción de ajuste)
        platformTreasuryService.recordProductSaleRefund(
            totalCommissionToRefund,
            refundReferenceId,
            "Refund commission from cancelled purchase with id: " + purchase.getId() + ". Reason: " + reason
        );
        
        // 4. Devolver stock
        for (PurchaseItem item : purchase.getItems()) {
            Product product = item.getProduct();
            product.incrementStock(item.getQuantity());
            productRepository.save(product);
        }
    }
    
    /**
     * Reembolsa un item específico
     */
    @Transactional
    protected void refundPurchaseItem(Purchase purchase, PurchaseItem item, String reason) {
        String refundReferenceId = "REFUND-ITEM-" + item.getId() + "-" + UUID.randomUUID();
        
        BigDecimal itemSubtotal = item.getSubtotal();
        BigDecimal commission = itemSubtotal.multiply(COMMISSION_RATE);
        BigDecimal netAmount = itemSubtotal.subtract(commission);
        
        // 1. Devolver al comprador (solo el subtotal del item)
        Wallet buyerWallet = walletService.getByOwnerId(purchase.getConsumer().getUser().getId());
        buyerWallet.addBalance(itemSubtotal);
        
        Transaction buyerRefundTx = Transaction.createProductPurchaseRefundTransaction(
            buyerWallet.getId(),
            itemSubtotal,
            refundReferenceId
        );
        transactionRepository.save(buyerRefundTx);
        
        // 2. Quitar del vendedor
        Wallet sellerWallet = walletService.getByOwnerId(item.getSeller().getUser().getId());
        sellerWallet.subtractBalance(netAmount);
        
        Transaction sellerRefundTx = Transaction.createProductSaleRefundTransaction(
            sellerWallet.getId(),
            netAmount.negate(),
            refundReferenceId
        );
        transactionRepository.save(sellerRefundTx);
        
        // 3. ✅ Restar comisión de la tesorería
        platformTreasuryService.recordProductSaleRefund(
            commission,
            refundReferenceId,
            "Refund commission from cancelled item #" + item.getId() + 
            " of purchase #" + purchase.getId() + ". Reason: " + reason
        );
        
        // 4. Devolver stock
        Product product = item.getProduct();
        product.incrementStock(item.getQuantity());
        productRepository.save(product);
    }
}
