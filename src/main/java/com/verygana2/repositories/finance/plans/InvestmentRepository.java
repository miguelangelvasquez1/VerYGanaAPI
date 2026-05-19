package com.verygana2.repositories.finance.plans;
 
import java.util.List;
import java.util.Optional;
 
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
 
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.Investment;
 
@Repository
public interface InvestmentRepository extends JpaRepository<Investment, Long> {
 
    /**
     * Lookup principal desde el webhook.
     * Busca el Investment pendiente de confirmación por la referencia Wompi.
     */
    Optional<Investment> findByWompiReference(String wompiReference);
 
    /**
     * Historial de depósitos de un wallet ordenado por fecha.
     */
    List<Investment> findByWalletOrderByCreatedAtDesc(Wallet wallet);
 
    /**
     * Depósitos confirmados de un wallet — para calcular el total invertido.
     */
    List<Investment> findByWalletAndConfirmedTrue(Wallet wallet);
}