package com.verygana2.services;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.purchase.requests.CreatePurchaseItemRequestDTO;
import com.verygana2.dtos.purchase.requests.CreatePurchaseRequestDTO;
import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.models.Transaction;
import com.verygana2.models.Wallet;
import com.verygana2.models.enums.products.PaymentMethod;
import com.verygana2.models.enums.products.PurchaseStatus;
import com.verygana2.models.products.Product;
import com.verygana2.models.products.Purchase;
import com.verygana2.models.products.PurchaseItem;
import com.verygana2.models.userDetails.SellerDetails;
import com.verygana2.repositories.PurchaseRepository;
import com.verygana2.repositories.TransactionRepository;
import com.verygana2.services.interfaces.ProductService;
import com.verygana2.services.interfaces.PurchaseService;
import com.verygana2.services.interfaces.WalletService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {

    private final WalletService walletService;

    private final TransactionRepository transactionRepository;

    private final PurchaseRepository purchaseRepository;

    private final ProductService productService;

    private final ConsumerDetailsService consumerDetailsService;

    @Override
    public Purchase createPurchase(Long consumerId, CreatePurchaseRequestDTO request) {

        // 1. Crear la compra
        Purchase purchase = new Purchase();
        purchase.setConsumer(consumerDetailsService.getConsumerById(consumerId));
        purchase.setStatus(PurchaseStatus.PENDING);
        purchase.setPaymentMethod(PaymentMethod.WALLET);
        purchase.setDeliveryAddress(request.getDeliveryAddress());
        // ... set otros campos

        // 2. Agregar items
        for (CreatePurchaseItemRequestDTO itemRequest : request.getItems()) {
            Product product = productService.getById(itemRequest.getProductId());

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

        // 4. Validar y bloquear saldo del comprador
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
            purchase.setStatus(PurchaseStatus.CONFIRMED);
        } catch (Exception e) {
            // Si falla, desbloquear el saldo
            buyerWallet.unblockBalance(purchase.getTotalAmount());
            purchase.setStatus(PurchaseStatus.PAYMENT_FAILED);
            throw e;
        }

        // 7. Actualizar inventarios
        for (PurchaseItem item : purchase.getItems()) {
            item.getProduct().decrementStock(item.getQuantity());
        }

        return purchaseRepository.save(purchase);
    }

    private void processPurchasePayment(Purchase purchase, Wallet buyerWallet) {
        String referenceId = purchase.getReferenceId();

        // 1. Desbloquear y restar saldo del comprador
        buyerWallet.unblockBalance(purchase.getTotalAmount());
        buyerWallet.subtractBalance(purchase.getTotalAmount());

        // 2. Crear transacción del comprador (débito)
        Transaction buyerTx = Transaction.createProductPurchaseTransaction(
                buyerWallet.getId(),
                purchase.getTotalAmount().negate(), // Negativo porque es débito
                referenceId);
        transactionRepository.save(buyerTx);

        // 3. Agrupar items por vendedor y crear transacciones
        Map<SellerDetails, BigDecimal> sellerAmounts = new HashMap<>();

        for (PurchaseItem item : purchase.getItems()) {
            SellerDetails seller = item.getSeller();
            BigDecimal currentAmount = sellerAmounts.getOrDefault(seller, BigDecimal.ZERO);
            sellerAmounts.put(seller, currentAmount.add(item.getSubtotal()));
        }

        // 4. Crear transacciones para cada vendedor (crédito)
        for (Map.Entry<SellerDetails, BigDecimal> entry : sellerAmounts.entrySet()) {
            SellerDetails seller = entry.getKey();
            BigDecimal amount = entry.getValue();

            // Calcular comisión de la plataforma (ej: 10%)
            BigDecimal platformFee = amount.multiply(new BigDecimal("0.10"));
            BigDecimal sellerAmount = amount.subtract(platformFee);

            // Agregar saldo al vendedor
            Wallet sellerWallet = walletService.getByOwnerId(seller.getUser().getId());
            sellerWallet.addBalance(sellerAmount);

            // Crear transacción del vendedor
            Transaction sellerTx = Transaction.createProductSaleTransaction(
                    sellerWallet.getId(),
                    sellerAmount,
                    referenceId);
            transactionRepository.save(sellerTx);

            // Opcional: Crear transacción de comisión para la plataforma
            // ...
        }
    }

    // Método para obtener todas las transacciones de una compra
    @Override
    public List<Transaction> getPurchaseTransactions(Long purchaseId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ObjectNotFoundException("Purchase with id: " + purchaseId + " not found", Purchase.class));

        return transactionRepository.findByReferenceId(purchase.getReferenceId());
    }

}
