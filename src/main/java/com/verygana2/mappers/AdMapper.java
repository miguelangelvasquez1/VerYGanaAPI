package com.verygana2.mappers;

import java.util.List;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.verygana2.dtos.ad.requests.AdCreateDTO;
import com.verygana2.dtos.ad.requests.AdUpdateDTO;
import com.verygana2.dtos.ad.responses.AdResponseDTO;
import com.verygana2.models.ads.Ad;

@Mapper(componentModel = "spring")
public interface AdMapper {

    // 🔹 Crear entidad a partir de DTO
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "advertiser", ignore = true)
    @Mapping(target = "likes", ignore = true)
    @Mapping(target = "status", expression = "java(com.verygana2.models.enums.AdStatus.PENDING)")
    @Mapping(target = "createdAt", expression = "java(java.time.ZonedDateTime.now())")
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "spentBudget", ignore = true)
    @Mapping(target = "currentLikes", constant = "0")
    @Mapping(target = "contentUrl", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "totalBudget", ignore = true)
    @Mapping(target = "targetMunicipalities", ignore = true) // Mapeo manual en el servicio
    @Mapping(target = "targetGender", ignore = true) // Mapeo manual en el servicio
    Ad toEntity(AdCreateDTO dto);

    // 🔹 Mapear entidad a DTO de respuesta
    @Mapping(target = "remainingBudget", expression = "java(entity.getRemainingBudget())")
    @Mapping(target = "remainingLikes", expression = "java(entity.getRemainingLikes())")
    @Mapping(target = "completionPercentage", expression = "java(entity.getCompletionPercentage())")
    @Mapping(target = "advertiserId", source = "advertiser.id")
    @Mapping(target = "advertiserName", source = "advertiser.companyName")
    @Mapping(target = "advertiserEmail", source = "advertiser.user.email")
    @Mapping(target = "hasUserLiked", ignore = true)
    AdResponseDTO toDto(Ad entity);

    // 🔹 Actualizar entidad existente desde un DTO
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "advertiser", ignore = true)
    @Mapping(target = "likes", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", expression = "java(java.time.ZonedDateTime.now())")
    @Mapping(target = "spentBudget", ignore = true)
    @Mapping(target = "currentLikes", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "totalBudget", ignore = true)
    @Mapping(target = "targetMunicipalities", ignore = true) // Mapeo manual en el servicio
    @Mapping(target = "targetGender", ignore = true) // Mapeo manual en el servicio
    void updateEntityFromDto(AdUpdateDTO dto, @MappingTarget Ad entity); //Permite campos opcionales

    // 🔹 Listado (opcional)
    List<AdResponseDTO> toDtoList(List<Ad> entities);
}