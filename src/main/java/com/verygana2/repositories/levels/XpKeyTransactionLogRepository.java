
package com.verygana2.repositories.levels;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.levels.XpKeyTransactionLog;

public interface XpKeyTransactionLogRepository
        extends JpaRepository<XpKeyTransactionLog, Long> {

    // El índice (consumer_id, created_at DESC) hace esto muy eficiente
    Page<XpKeyTransactionLog> findByConsumerIdOrderByCreatedAtDesc(
            Long consumerId, Pageable pageable
    );
}
