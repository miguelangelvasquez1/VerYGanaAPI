package com.verygana2.utils.Locations;


import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.verygana2.models.Department;
import com.verygana2.models.Municipality;
import com.verygana2.repositories.DepartmentRepository;
import com.verygana2.repositories.MunicipalityRepository;

@Service
public class LocationImportService {

    private final DaneApiService daneApiService;
    private final DepartmentRepository departmentRepository;
    private final MunicipalityRepository municipalityRepository;

    public LocationImportService(
            DaneApiService daneApiService,
            DepartmentRepository departmentRepository,
            MunicipalityRepository municipalityRepository
    ) {
        this.daneApiService = daneApiService;
        this.departmentRepository = departmentRepository;
        this.municipalityRepository = municipalityRepository;
    }

    @Transactional
    public void importFromDane() {
        List<JsonNode> records = daneApiService.fetchRawRecords();

        // Map para acumular departamentos por código
        Map<String, Department> departmentsMap = new HashMap<>();

        // Primero: recorrer y construir municipios temporales
        List<Municipality> municipalitiesToSave = new ArrayList<>();

        for (JsonNode rec : records) {
            Optional<String> maybeMuniCode = daneApiService.extractMunicipalityCode(rec);
            if (maybeMuniCode.isEmpty()) {
                // saltar registro si no encontramos código
                continue;
            }
            String muniCode = maybeMuniCode.get(); // e.g. "05001" o "63001"
            if (muniCode.length() != 5) continue; // validación simple

            // department code = primeros dos dígitos
            String deptCode = muniCode.substring(0, 2);

            // nombre municipio (fallback si no existe)
            String muniName = daneApiService.extractName(rec, "municipio").orElse("UNKNOWN");

            // nombre departamento (si viene, úsalo; si no, lo pondremos temporalmente como "DEPT {code}")
            String deptName = daneApiService.extractName(rec, "departamento")
                    .orElse("DEPARTAMENTO " + deptCode);

            // crear/actualizar mapa de departamentos
            departmentsMap.computeIfAbsent(deptCode, k ->
                    Department.builder()
                              .code(k)
                              .name(deptName)
                              .build()
            );

            // crear municipio y asociar el departamento (sin persistir todavía)
            Municipality m = new Municipality();
            m.setCode(muniCode);
            m.setName(muniName);

            // asociamos el objeto Department temporal — JPA resolverá FK cuando el Department sea persistido
            m.setDepartment(departmentsMap.get(deptCode));

            municipalitiesToSave.add(m);
        }

        // Guardar/actualizar departamentos (bulk)
        List<Department> departments = new ArrayList<>(departmentsMap.values());
        // Si existieran con el mismo código, saveAll actualizará (merge) — depende de equals/hash en entidad y del ID.
        departmentRepository.saveAll(departments);

        // Refrescar: traer departamentos guardados desde BD (para que sean managed y tengan estado correcto)
        List<String> deptCodes = departments.stream().map(Department::getCode).collect(Collectors.toList());
        Map<String, Department> persistedDepartments = departmentRepository.findAllById(
                Objects.requireNonNull(deptCodes)
        ).stream().collect(Collectors.toMap(Department::getCode, d -> d));

        // Asignar departamento persistido a cada municipio y guardar municipios
        for (Municipality m : municipalitiesToSave) {
            String deptCode = m.getCode().substring(0, 2);
            Department dep = persistedDepartments.get(deptCode);
            m.setDepartment(dep);
        }

        // Guardar municipios (se usa saveAll para rendimiento)
        municipalityRepository.saveAll(municipalitiesToSave);
    }
}
