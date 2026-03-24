package com.verygana2.models.userDetails;

import java.util.ArrayList;
import java.util.List;

import com.verygana2.models.Avatar;
import com.verygana2.models.Category;
import com.verygana2.models.enums.Gender;
import com.verygana2.models.products.FavoriteProduct;
import com.verygana2.models.raffles.RaffleTicket;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Data
@EqualsAndHashCode(callSuper = false)
public class ConsumerDetails extends UserDetails {

    private String userHash;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    @Column(nullable = false, length = 20)
    private String userName;

    // ConsumerDetails — DESPUÉS (correcto)
    @NotNull(message = "Avatar is required")  // ← NotNull para objetos/entidades
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avatar_id")
    private Avatar avatar;

    @NotBlank(message = "the name cannot be empty")
    @Size(max = 50)
    private String name;
    @NotBlank(message = "the name cannot be empty")
    @Size(max = 50)
    private String lastName;
    @Min(value = 0, message = "adsWatched must be zero or positive")
    private Integer adsWatched;

    @Min(value = 0, message = "totalWithdraws must be zero or positive")
    private Integer totalWithdraws;

    @Min(value = 0, message = "dailyAdCount must be zero or positive")
    @Max(value = 100, message = "dailyAdCount cannot exceed 100")
    private Integer dailyAdCount;


    @NotBlank(message = "Department is required")
    @Size(max = 50)
    private String department;

    @NotBlank(message = "Municipality is required")
    @Size(max = 50)
    private String municipality;

    private Integer age;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private boolean hasPet;
    
    @ManyToMany
    @JoinTable(name = "consumer_preferences", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "category_id"))
    @NotNull(message = "Preferences are required")
    @Size(min = 1, message = "At least one category must be selected")
    private List<Category> categories = new ArrayList<>();

    @OneToMany(mappedBy = "consumer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FavoriteProduct> favoriteProducts = new ArrayList<>();

    @OneToMany(mappedBy = "ticketOwner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RaffleTicket> raffleTickets = new ArrayList<>();

    @PrePersist
    public void onCreate (){
        this.hasPet = false;
    }

    @Column(name = "referral_code", nullable = false, length = 16, updatable = false)
    private String referralCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_by_consumer_id")
    private ConsumerDetails referredBy;

    @OneToMany(mappedBy = "referredBy")
    private List<ConsumerDetails> referrals = new ArrayList<>();

}
