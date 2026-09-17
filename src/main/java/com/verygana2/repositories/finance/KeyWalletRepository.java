package com.verygana2.repositories.finance;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.KeyWallet;

@Repository
public interface KeyWalletRepository extends JpaRepository<KeyWallet, UUID> {

    Optional<KeyWallet> findByConsumerId(Long consumerId);

    boolean existsByConsumerId (Long consumerId);

    /**
     * Pasivo total de llaves vivas, en centavos de COP.
     *
     * Es la contraparte de TreasuryAccount(KEYS_RESERVE): cada centavo de llave
     * que un consumidor tiene en su billetera es un centavo que la app debe
     * respaldar cuando lo redima en un copago.
     *
     * Suma los cuatro campos, no getAvailableKeysCents(): las llaves bloqueadas
     * por un copago en curso siguen siendo pasivo — o se confirman (y drenan
     * KEYS_RESERVE) o se liberan (y vuelven a estar disponibles).
     *
     * Misma escala que la tesorería: 1 centavo de billetera = 1 centavo de
     * KEYS_RESERVE. keyValueCents solo divide para mostrar "llaves" en la UI.
     */
    @Query("""
            SELECT COALESCE(SUM(w.purchaseKeysCents + w.blockedPurchaseKeysCents
                              + w.connectivityKeysCents + w.blockedConnectivityKeysCents), 0)
            FROM KeyWallet w
            """)
    long sumLiveKeyLiabilityCents();
}
