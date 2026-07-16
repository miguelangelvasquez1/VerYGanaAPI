package com.verygana2.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.EmailVerificationCode;

@Repository
public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {
    Optional<EmailVerificationCode> findTopByEmailOrderByIdDesc(String email);
    void deleteByEmail(String email);
    long countByEmailAndCreatedAtAfter(String email, java.time.LocalDateTime after);
    void deleteByEmailAndCreatedAtBefore(String email, java.time.LocalDateTime before);
}
