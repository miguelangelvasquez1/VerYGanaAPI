package com.verygana2.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.Municipality;

@Repository
public interface MunicipalityRepository extends JpaRepository<Municipality, String> {
    List<Municipality> findByDepartmentCode(String departmentCode);
}