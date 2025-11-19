package com.verygana2.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.DepartmentResponseDTO;
import com.verygana2.dtos.MunicipalityResponseDTO;
import com.verygana2.mappers.LocationMapper;
import com.verygana2.repositories.DepartmentRepository;
import com.verygana2.repositories.MunicipalityRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final DepartmentRepository departmentRepository;
    private final MunicipalityRepository municipalityRepository;
    private final LocationMapper locationMapper;

    @Transactional(readOnly = true)
    @Cacheable("departments")
    public List<DepartmentResponseDTO> getAllDepartments() {
        log.debug("Obteniendo todos los departamentos");
        return departmentRepository.findAll().stream()
                .map(locationMapper::toDepartmentDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DepartmentResponseDTO getDepartmentByCode(String code) {
        return departmentRepository.findById(code)
                .map(locationMapper::toDepartmentDto)
                .orElseThrow(() -> new EntityNotFoundException("Departamento no encontrado: " + code));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "municipalities", key = "#departmentCode")
    public List<MunicipalityResponseDTO> getMunicipalitiesByDepartment(String departmentCode) {
        log.debug("Obteniendo municipios del departamento: {}", departmentCode);
        return municipalityRepository.findByDepartmentCode(departmentCode).stream()
                .map(locationMapper::toMunicipalityDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MunicipalityResponseDTO getMunicipalityByCode(String code) {
        return municipalityRepository.findById(code)
                .map(locationMapper::toMunicipalityDto)
                .orElseThrow(() -> new EntityNotFoundException("Municipio no encontrado: " + code));
    }
}