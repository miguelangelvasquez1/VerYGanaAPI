package com.verygana2.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByReference(String reference);
    Optional<Payment> findByWompiTransactionId(String wompiTransactionId);
    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);
}
