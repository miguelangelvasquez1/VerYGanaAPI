package com.VerYGana.models;

import java.time.LocalDateTime;
import java.util.List;

import com.VerYGana.models.Enums.UserState;
import com.VerYGana.security.auth.UserRegisterRequest;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "Users")
@Data
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String password; //JsonIgnore, encrypt
    private Integer adsWatched;
    private Integer totalWithdraws;
    private Integer dailyAdCount;
    private UserState userState;
    private LocalDateTime registeredDate;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private Wallet wallet;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Transaction> transactions;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<PayoutMethod> payoutMethods;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Notification> notifications;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<RaffleTicket> raffleTickets;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserVerification verification;

    public User(UserRegisterRequest userRegisterRequest) {
        this.name = userRegisterRequest.getName();
        this.email = userRegisterRequest.getEmail();
        this.phoneNumber = userRegisterRequest.getPhoneNumber();
        this.password = userRegisterRequest.getPassword(); // Encriptar la contraseña en el futuro
        this.adsWatched = 0;
        this.totalWithdraws = 0;
        this.userState = UserState.ACTIVE;
        this.registeredDate = LocalDateTime.now();
    }
}

