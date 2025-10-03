package com.VerYGana.models;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import com.VerYGana.dtos2.auth.UserRegisterRequest;
import com.VerYGana.models.enums2.Role;
import com.VerYGana.models.enums2.UserState;
import com.VerYGana.models.raffles.RaffleTicket;
import com.VerYGana.models.userDetails2.UserDetails;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
public class User{

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserDetails userDetails;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String phoneNumber;
    @JsonIgnore
    private String password; //JsonIgnore, encrypt
    private UserState userState;
    private ZonedDateTime registeredDate;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private Wallet wallet;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<PayoutMethod> payoutMethods;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Notification> notifications;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<RaffleTicket> raffleTickets;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserVerification verification;

    public User(UserRegisterRequest userRegisterRequest) {
        this.email = userRegisterRequest.getEmail();
        this.role = userRegisterRequest.getRole();
        this.phoneNumber = userRegisterRequest.getPhoneNumber();
        this.password = userRegisterRequest.getPassword();
        this.userState = UserState.ACTIVE;
        this.registeredDate = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }
}

