package com.verygana2.mappers.pet;



import com.verygana2.dtos.pet.PetSceneObjectResponseDTO;
import com.verygana2.dtos.pet.PetSceneResponseDTO;
import com.verygana2.models.pets.PetScene;
import com.verygana2.models.pets.PetSceneObject;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PetSceneMapper {

    @Mapping(target = "id", source = "objectId")
    @Mapping(target = "url", ignore = true)
    PetSceneObjectResponseDTO toObjectDTO(PetSceneObject obj);

    PetSceneResponseDTO toSceneDTO(PetScene scene);
}