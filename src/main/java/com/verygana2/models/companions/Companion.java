package com.verygana2.models.companions;

import java.time.LocalDateTime;

import com.verygana2.models.enums.companions.EvolutionStage;
import com.verygana2.models.userDetails.ConsumerDetails;

//import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//@Entity
@Table(name = "companions")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Companion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne
    private ConsumerDetails consumer;
    private String name;
    private Integer currentLevel;
    private Long experienceToNextLevel;
    private EvolutionStage evolutionStage;
    private LocalDateTime createdAt;
    private LocalDateTime lastInteractionDate;

    @PrePersist
    protected void onCreate (){
        this.currentLevel = 0;
        this.evolutionStage = EvolutionStage.BABY;
        this.createdAt = LocalDateTime.now();
        this.lastInteractionDate = LocalDateTime.now();
    }
}
