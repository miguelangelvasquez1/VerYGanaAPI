package com.verygana2.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.targeting.TargetAudienceResponseDTO;
import com.verygana2.models.Municipality;
import com.verygana2.models.TargetAudience;

@Mapper(componentModel = "spring")
public interface TargetAudienceMapper {

    @Mapping(target = "municipalityCodes", source = "targetMunicipalities")
    TargetAudienceResponseDTO toResponseDTO(TargetAudience targetAudience);

    default String map(Municipality municipality) {
        return municipality == null ? null : municipality.getCode();
    }
}
