package com.verygana2.mappers;

import java.util.List;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.verygana2.dtos.MunicipalityResponseDTO;
import com.verygana2.dtos.ad.requests.AdCreateDTO;
import com.verygana2.dtos.ad.requests.AdUpdateDTO;
import com.verygana2.dtos.ad.responses.AdForAdminDTO;
import com.verygana2.dtos.ad.responses.AdForConsumerDTO;
import com.verygana2.dtos.ad.responses.AdResponseDTO;
import com.verygana2.models.Municipality;
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
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "totalBudget", ignore = true)
    @Mapping(target = "targetMunicipalities", ignore = true) // Mapeo manual en el servicio
    @Mapping(target = "duration", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "asset", ignore = true)
    Ad toEntity(AdCreateDTO dto);

    // 🔹 Mapear entidad a DTO de respuesta
    @Mapping(target = "remainingBudget", expression = "java(entity.getRemainingBudget())")
    @Mapping(target = "remainingLikes", expression = "java(entity.getRemainingLikes())")
    @Mapping(target = "completionPercentage", expression = "java(entity.getCompletionPercentage())")
    @Mapping(target = "contentUrl", ignore = true)
    AdResponseDTO toDto(Ad entity);
    @Mapping(target = "departmentName", source = "department.name")
    MunicipalityResponseDTO municipalityToDto(Municipality municipality);

    // 🔹 Mapear entidad a DTO para consumidor
    @Mapping(target = "advertiserName", expression = "java(ad.getAdvertiser() != null ? ad.getAdvertiser().getCompanyName() : null)")
    @Mapping(target = "advertiserId", expression = "java(ad.getAdvertiser() != null ? ad.getAdvertiser().getId() : null)")
    @Mapping(target = "sessionUUID", ignore = true)
    @Mapping(target = "contentUrl", ignore = true)
    @Mapping(target = "mediaType", ignore = true)
    AdForConsumerDTO toConsumerDto(Ad ad);

    // Mapear entidad a DTO para administrador
    @Mapping(target = "advertiserName", expression = "java(ad.getAdvertiser() != null ? ad.getAdvertiser().getCompanyName() : null)")
    @Mapping(target = "advertiserId", expression = "java(ad.getAdvertiser() != null ? ad.getAdvertiser().getId() : null)")
    @Mapping(target = "contentUrl", ignore = true)
    @Mapping(target = "mediaType", ignore = true)
    AdForAdminDTO toAdminDto(Ad ad);

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
    @Mapping(target = "duration", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "asset", ignore = true)
    void updateEntityFromDto(AdUpdateDTO dto, @MappingTarget Ad entity); //Permite campos opcionales

    // 🔹 Listado (opcional)
    List<AdResponseDTO> toDtoList(List<Ad> entities);
}