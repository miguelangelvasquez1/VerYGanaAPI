package com.Rifacel.models;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Notification {
    private String id;
    private String userId;
    private String message;
    private LocalDateTime dateSent;
    private boolean read;
}
