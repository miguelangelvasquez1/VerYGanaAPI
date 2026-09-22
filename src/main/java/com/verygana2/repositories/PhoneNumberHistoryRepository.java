package com.verygana2.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.PhoneNumberHistory;

public interface PhoneNumberHistoryRepository
        extends JpaRepository<PhoneNumberHistory, Long> {

    List<PhoneNumberHistory> findByUserIdOrderByAssignedAtAsc(Long userId);

    List<PhoneNumberHistory> findByPhoneNumberAndDetachedAtIsNull(String phoneNumber);

    boolean existsByPhoneNumberAndDetachedAtIsNull(String phoneNumber);
}