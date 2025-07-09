package com.Rifacel.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Rifacel.models.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByUserIdOrderByDateSentDesc(String userId);
    long countByUserIdAndIsReadFalse(String userId);
}
