package com.verygana2.repositories.finance;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.PayoutItem;

@Repository
public interface PayoutItemRepository extends JpaRepository<PayoutItem, UUID> {

    /**
     * Verifica si un PurchaseItem ya entró a un payout. La restricción unique
     * en purchase_item_id ya lo garantiza a nivel de BD; esto evita el
     * intento de insert duplicado en el job diario.
     */
    boolean existsByPurchaseItemId(Long purchaseItemId);
}
