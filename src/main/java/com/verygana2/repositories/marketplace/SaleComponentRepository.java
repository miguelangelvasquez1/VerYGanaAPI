package com.verygana2.repositories.marketplace;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.marketplace.SaleComponentStatus;
import com.verygana2.models.enums.marketplace.SaleComponentType;
import com.verygana2.models.marketplace.SaleComponent;

@Repository
public interface SaleComponentRepository extends JpaRepository<SaleComponent, Long> {

    List<SaleComponent> findByPurchaseItemIdOrderByIdAsc(Long purchaseItemId);

    List<SaleComponent> findByPurchaseItemIdAndStatus(Long purchaseItemId, SaleComponentStatus status);

    Optional<SaleComponent> findByPurchaseItemIdAndComponentTypeAndStatus(Long purchaseItemId,
            SaleComponentType componentType, SaleComponentStatus status);

    /**
     * Neto de los pagos de VERYGANA por fundingRef: reconocidos menos reversados. Debe
     * coincidir con lo que tesorería movió desde FORTIFICATION para ese mismo recurso.
     * Cada fila: {@code [sourceRef (String), neto (Long)]}.
     */
    @Query("""
            SELECT sc.sourceRef,
                   SUM(CASE WHEN sc.status = com.verygana2.models.enums.marketplace.SaleComponentStatus.RECOGNIZED
                            THEN sc.amountCents ELSE -sc.amountCents END)
            FROM SaleComponent sc
            WHERE sc.componentType = com.verygana2.models.enums.marketplace.SaleComponentType.VERYGANA_PAYMENT
            GROUP BY sc.sourceRef
            """)
    List<Object[]> sumNetVeryganaPaymentsBySourceRef();

    @Query("""
            SELECT COALESCE(SUM(sc.amountCents), 0) FROM SaleComponent sc
            WHERE sc.purchaseItem.id = :itemId AND sc.status = :status
            """)
    long sumByItemAndStatus(@Param("itemId") Long itemId, @Param("status") SaleComponentStatus status);
}
