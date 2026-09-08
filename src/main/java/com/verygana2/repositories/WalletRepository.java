package com.verygana2.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.finance.WalletStatus;
import com.verygana2.models.finance.Wallet;

import jakarta.persistence.LockModeType;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    // Solo para lectura. Toda ruta que MODIFIQUE el saldo (consume/deposit + save)
    // debe usar findByCommercialIdForUpdate para serializar los read-modify-write
    // concurrentes sobre la misma fila.
    Optional<Wallet> findByCommercialId(Long commercialId);

    /**
     * SELECT ... FOR UPDATE sobre el wallet. Úsese en TODA operación que cambie el
     * saldo (BudgetService, creación de anuncios/encuestas, reembolsos, recargas):
     * serializa por comercial los ajustes concurrentes y evita mezclar bloqueo
     * pesimista con el optimista de {@code @Version}, que era la principal fuente
     * de deadlocks. {@code FOR UPDATE} se comporta igual en MySQL y en PostgreSQL.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.commercial.id = :commercialId")
    Optional<Wallet> findByCommercialIdForUpdate(@Param("commercialId") Long commercialId);

    boolean existsByCommercialId (Long commercialId);

    List<Wallet> findByStatusIn(List<WalletStatus> statuses);
}
