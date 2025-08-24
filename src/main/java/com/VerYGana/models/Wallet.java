package com.VerYGana.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Version;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import lombok.Data;

@Entity
@Data
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  
    @Version
    private Long version;
    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    private BigDecimal balance;
    private BigDecimal blockedBalance;
    private LocalDateTime lastUpdated;
}

