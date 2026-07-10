package com.verygana2.mappers.pqrs;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import com.verygana2.dtos.pqrs.responses.PqrsAdminDetailDTO;
import com.verygana2.dtos.pqrs.responses.PqrsResponseDTO;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.utils.pqrs.RequesterNameResolver;

@Mapper(componentModel = "spring")
public abstract class PqrsMapper {

    @Autowired
    protected RequesterNameResolver requesterNameResolver;

    // "radicado" se resuelve automáticamente desde Pqrs.getBased() (mismo nombre de propiedad).
    public abstract PqrsResponseDTO toResponseDTO(Pqrs pqrs);

    @Mapping(target = "requesterId", source = "requester.id")
    @Mapping(target = "requesterEmail", source = "requester.email")
    @Mapping(target = "requesterPhone", source = "requester.phoneNumber")
    @Mapping(target = "requesterName", ignore = true)
    public abstract PqrsAdminDetailDTO toAdminDetailDTO(Pqrs pqrs);

    @AfterMapping
    protected void setAdminComputedFields(@MappingTarget PqrsAdminDetailDTO dto, Pqrs pqrs) {
        dto.setRequesterName(requesterNameResolver.resolve(pqrs.getRequester()));
    }
}
