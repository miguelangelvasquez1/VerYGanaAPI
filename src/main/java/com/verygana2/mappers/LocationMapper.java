package com.verygana2.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.DepartmentResponseDTO;
import com.verygana2.dtos.MunicipalityResponseDTO;
import com.verygana2.models.Department;
import com.verygana2.models.Municipality;

@Mapper(
    componentModel = "spring")
public interface LocationMapper {

    /**
     * Convierte Department a DepartmentResponseDTO
     */
    DepartmentResponseDTO toDepartmentDto(Department department);

    /**
     * Convierte Municipality a MunicipalityResponseDTO
     */
    @Mapping(source = "department.code", target = "departmentCode")
    @Mapping(source = "department.name", target = "departmentName")
    MunicipalityResponseDTO toMunicipalityDto(Municipality municipality);
}