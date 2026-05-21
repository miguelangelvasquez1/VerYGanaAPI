package com.verygana2.models.pets;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pet_scenes")
@Data
@NoArgsConstructor
public class PetScene {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer sceneId;
    private Boolean active = true;

    @OneToMany(mappedBy = "scene", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PetSceneObject> objects = new ArrayList<>();
}