package com.VerYGana.services.interfaces;

public interface NotificationService {
    // List<Notification> getByUserIdOrderByDateSentDesc(String userId);
    long getCountByUserIdAndReadFalse(Long userId);
}
