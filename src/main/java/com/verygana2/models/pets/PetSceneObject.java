package com.verygana2.models.pets;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pet_scene_objects")
@Data
@NoArgsConstructor
public class PetSceneObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String objectId;
    private String type;
    @Column(name = "object_key")
    private String objectKey;
    private Integer x;
    private Integer y;
    private Integer width;
    private Integer height;
    private Double scaleMultiplier = 1.0;

    @ManyToOne
    @JoinColumn(name = "scene_id")
    private PetScene scene;
}