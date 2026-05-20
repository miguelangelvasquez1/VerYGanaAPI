package com.verygana2.mappers.pet;



import com.verygana2.dtos.pet.PetNotificationRequestDTO;
import com.verygana2.dtos.pet.PetNotificationResponseDTO;
import com.verygana2.models.pets.PetNotification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PetNotificationMapper {

    @Mapping(target = "id", source = "externalId")
    PetNotificationResponseDTO toResponseDTO(PetNotification notification);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "read", ignore = true)
    PetNotification toEntity(PetNotificationRequestDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "read", ignore = true)
    void updateFromDto(PetNotificationRequestDTO dto, @MappingTarget PetNotification notification);
}
