package com.Rifacel.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Rifacel.models.Notification;
import com.Rifacel.repositories.NotificationRepository;
import com.Rifacel.services.interfaces.NotificationService;

@Service
public class NotificationServiceImpl implements NotificationService{
    
    @Autowired
    private NotificationRepository notificationRepository;

    @Override
    public List<Notification> getByUserIdOrderByDateSentDesc(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("invalid userId");
        }
        return notificationRepository.findByUserIdOrderByDateSentDesc(userId);
    }

    @Override
    public long getCountByUserIdAndReadFalse(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("invalid userId");
        }
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }
}
