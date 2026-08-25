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

    /**
     * Id con el que el juego identifica el ítem. Único: {@code findByExternalId}
     * devuelve un Optional, así que dos filas con el mismo número hacen fallar toda
     * compra de ese ítem con un 500. Admite null — la ropa se resuelve por nombre.
     */
    @Column(unique = true)
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