package com.verygana2.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.Department;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, String> {
}