package com.VerYGana.models;

import java.time.ZonedDateTime;
import java.util.List;

import com.VerYGana.models.Enums.WalletOwnerType;
import com.VerYGana.models.Interfaces.WalletOwner;
import com.VerYGana.security.auth.AdvertiserRegisterRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class Advertiser implements WalletOwner{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String companyName;
    @JsonIgnore
    private String password;
    private String email;
    private String phoneNumber;
    private ZonedDateTime createdAt;

    @OneToMany(mappedBy = "advertiser")
    private List<Ad> ads;

    @OneToOne(mappedBy = "advertiser", cascade = CascadeType.ALL)
    private Wallet wallet;

    @OneToMany(mappedBy = "advertiser", cascade = CascadeType.ALL)
    private List<PayoutMethod> payoutMethods;

    @OneToMany(mappedBy = "advertiser", cascade = CascadeType.ALL)
    private List<Notification> notifications;

    public Advertiser(AdvertiserRegisterRequest advertiserRegisterRequest) {
        this.companyName = advertiserRegisterRequest.getCompanyName();
        this.email = advertiserRegisterRequest.getEmail();
        this.phoneNumber = advertiserRegisterRequest.getPhoneNumber();
        this.password = advertiserRegisterRequest.getPassword();
    }

    @Override
    public WalletOwnerType getOwnerType() {
        return WalletOwnerType.ADVERTISER;
    }

    @Override
    public String getName() {
        return this.companyName;
    }
}
