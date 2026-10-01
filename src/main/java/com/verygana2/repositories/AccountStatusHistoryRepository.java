package com.verygana2.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.AccountStatusHistory;
import com.verygana2.models.enums.AccountStatus;

@Repository
public interface AccountStatusHistoryRepository extends JpaRepository<AccountStatusHistory, Long> {
    Optional<AccountStatusHistory> findFirstByUserIdAndToStatusOrderByOccurredAtDesc(Long userId, AccountStatus toStatus);
}
