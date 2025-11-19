package com.verygana2.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.DepartmentResponseDTO;
import com.verygana2.dtos.MunicipalityResponseDTO;
import com.verygana2.services.LocationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
@Tag(name = "Locations", description = "Gestión de ubicaciones geográficas")
public class LocationController {

    private final LocationService locationService;

    @Operation(summary = "Obtener todos los departamentos")
    @GetMapping("/departments")
    public ResponseEntity<List<DepartmentResponseDTO>> getAllDepartments() {
        return ResponseEntity.ok(locationService.getAllDepartments());
    }

    @Operation(summary = "Obtener departamento por código")
    @GetMapping("/departments/{code}")
    public ResponseEntity<DepartmentResponseDTO> getDepartmentByCode(@PathVariable String code) {
        return ResponseEntity.ok(locationService.getDepartmentByCode(code));
    }

    @Operation(summary = "Obtener municipios de un departamento")
    @GetMapping("/departments/{departmentCode}/municipalities")
    public ResponseEntity<List<MunicipalityResponseDTO>> getMunicipalitiesByDepartment(
            @PathVariable String departmentCode) {
        return ResponseEntity.ok(locationService.getMunicipalitiesByDepartment(departmentCode));
    }

    @Operation(summary = "Obtener municipio por código")
    @GetMapping("/municipalities/{code}")
    public ResponseEntity<MunicipalityResponseDTO> getMunicipalityByCode(@PathVariable String code) {
        return ResponseEntity.ok(locationService.getMunicipalityByCode(code));
    }
}
