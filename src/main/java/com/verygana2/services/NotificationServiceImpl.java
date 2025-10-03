package com.verygana2.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.verygana2.repositories.NotificationRepository;
import com.verygana2.services.interfaces.NotificationService;

@Service
public class NotificationServiceImpl implements NotificationService{
    
    @Autowired
    private NotificationRepository notificationRepository;

    // @Override
    // public List<Notification> getByUserIdOrderByDateSentDesc(String userId) {
    //     if (userId == null || userId.isBlank()) {
    //         throw new IllegalArgumentException("invalid userId");
    //     }
    //     return notificationRepository.findByUserIdOrderByDateSentDesc(userId);
    // }

    @Override
    public long getCountByUserIdAndReadFalse(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("invalid userId");
        }
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }
}
