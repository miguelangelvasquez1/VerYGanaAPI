package com.VerYGana.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.VerYGana.models.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    // List<Notification> findByUserIdOrderByDateSentDesc(String userId);
    long countByUserIdAndIsReadFalse(Long userId);
}
