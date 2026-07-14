package com.verygana2.repositories.finance;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.WompiTransaction;


@Repository
public interface WompiTransactionRepository extends JpaRepository<WompiTransaction, UUID> {

    /**
     * Busca por la referencia interna que enviamos al crear el checkout.
     * Es el campo clave para reconciliar webhooks con registros locales.
     */
    Optional<WompiTransaction> findByReference(String reference);

    /**
     * Busca por el ID real de Wompi.
     * Usado para evitar procesar el mismo webhook dos veces.
     */
    Optional<WompiTransaction> findByWompiId(String wompiId);

}
