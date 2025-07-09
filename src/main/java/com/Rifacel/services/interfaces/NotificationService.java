package com.Rifacel.services.interfaces;

import java.util.List;

import com.Rifacel.models.Notification;

public interface NotificationService {
    List<Notification> getByUserIdOrderByDateSentDesc(String userId);
    long getCountByUserIdAndReadFalse(String userId);
}
