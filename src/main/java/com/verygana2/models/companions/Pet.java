package com.verygana2.models.companions;

import com.verygana2.models.enums.companions.PetSpecies;

//import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

//@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper=false)
public class Pet extends Companion{
    private PetSpecies species;
    private String imageUrl;
}
