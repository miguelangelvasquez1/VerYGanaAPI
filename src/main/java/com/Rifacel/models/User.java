package com.Rifacel.models;

import java.time.LocalDateTime;

import com.Rifacel.models.Enums.UserState;
import com.Rifacel.security.auth.UserRegisterRequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name;

    private String lastNames;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String phoneNumber;

    @Column(nullable = false) //implementar seguridad con Json ignore
    private String password;

    private int adsWatched;

    private int totalWithDraws;

    private int dailyAdsCount;

    private LocalDateTime adsReset;

    private String bankAccount;

    private String paymentMethod; // e.g. Nequi, Bancolombia, etc.

    private UserState userState;

    private LocalDateTime registeredDate;

    public User(UserRegisterRequest userRegisterRequest) {
        this.name = userRegisterRequest.getName();
        this.email = userRegisterRequest.getEmail();
        this.phoneNumber = userRegisterRequest.getPhoneNumber();
        this.password = userRegisterRequest.getPassword(); // Encriptar la contraseña en el futuro
        this.adsWatched = 0;
        this.totalWithDraws = 0;
        this.userState = UserState.ACTIVE;
        this.adsReset = LocalDateTime.now().plusHours(24);
        this.registeredDate = LocalDateTime.now();
    }
}

