package com.VerYGana.services.interfaces;

import java.util.List;

import com.VerYGana.models.Notification;

public interface NotificationService {
    List<Notification> getByUserIdOrderByDateSentDesc(String userId);
    long getCountByUserIdAndReadFalse(String userId);
}
