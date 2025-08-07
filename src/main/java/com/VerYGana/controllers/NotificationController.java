package com.VerYGana.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.VerYGana.models.Notification;
import com.VerYGana.services.interfaces.NotificationService;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    
    @Autowired
    private NotificationService notificationService;

    // Obtener la lista de notificaciones de un usuario con su id como argumento y las ordena en orden descendente
    // @GetMapping("/{userId}")
    // public ResponseEntity<List<Notification>> getByUserIdOrderByDateSentDesc (@PathVariable String userId){
    //     List<Notification> foundNotifications = notificationService.getByUserIdOrderByDateSentDesc(userId);
    //     return ResponseEntity.ok(foundNotifications);
    // }

    // Obtener el número de notificaciones no leídas por el usuario pasando su id como argumento
    @GetMapping("/unread/{userId}")
    public ResponseEntity<Long> getCountByUserIdAndReadFalse(@PathVariable Long userId){
        Long count = notificationService.getCountByUserIdAndReadFalse(userId);
        return ResponseEntity.ok(count);
    }
}
