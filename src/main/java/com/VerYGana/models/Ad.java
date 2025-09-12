package com.VerYGana.models;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Data;

@Entity
@Data
public class Ad { //validar register y login, password hash
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;
    private Double rewardPerLike;
    private Integer maxLikes;
    private boolean isActive;
    private LocalDateTime createdAt;

    @ManyToOne
    private User advertiser;

    @OneToMany(mappedBy = "ad")
    private List<AdLike> likes;

    @OneToOne
    private Product product;
}
