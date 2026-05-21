package com.verygana2.models.pets;


import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pet_catalog_items")
@Data
@NoArgsConstructor
public class PetCatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer externalId;
    private String name;

    @Column(length = 500)
    private String description;

    private Boolean isMedicine;
    private Boolean isDrink;
    private Boolean curesAllParts;
    private Integer price;
    @Column(name = "sprite_object_key")
    private String spriteObjectKey;
    private Integer expWhenEating;
    private Integer healthDelta;
    private Integer energyDelta;
    private Integer hungerDelta;
    private Integer thirstDelta;
    private Integer hygieneDelta;
    private Integer humorDelta;
    private Integer bodyFatDelta;
    private Boolean active = true;
}