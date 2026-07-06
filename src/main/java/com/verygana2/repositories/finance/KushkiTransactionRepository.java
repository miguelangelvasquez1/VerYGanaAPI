package com.verygana2.repositories.finance;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.finance.KushkiTransaction;

public interface KushkiTransactionRepository extends JpaRepository<KushkiTransaction, UUID> {

    Optional<KushkiTransaction> findByKushkiTransferId(String kushkiTransferId);

    Optional<KushkiTransaction> findByInternalReference(String internalReference);

    boolean existsByKushkiTransferId(String kushkiTransferId);
}
